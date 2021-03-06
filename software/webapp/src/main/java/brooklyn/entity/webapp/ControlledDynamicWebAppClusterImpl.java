package brooklyn.entity.webapp;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.LoadBalancer;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class ControlledDynamicWebAppClusterImpl extends AbstractEntity implements ControlledDynamicWebAppCluster {

    public static final Logger log = LoggerFactory.getLogger(ControlledDynamicWebAppClusterImpl.class);

    public ControlledDynamicWebAppClusterImpl() {
        this(MutableMap.of(), null);
    }
    
    public ControlledDynamicWebAppClusterImpl(Map flags) {
        this(flags, null);
    }
    
    public ControlledDynamicWebAppClusterImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    
    public ControlledDynamicWebAppClusterImpl(Map flags, Entity parent) {
        super(flags, parent);
        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void init() {
        ConfigToAttributes.apply(this, FACTORY);
        ConfigToAttributes.apply(this, MEMBER_SPEC);
        ConfigToAttributes.apply(this, CONTROLLER);
        ConfigToAttributes.apply(this, CONTROLLER_SPEC);
        ConfigToAttributes.apply(this, WEB_CLUSTER_SPEC);
        
        ConfigurableEntityFactory<? extends WebAppService> webServerFactory = getAttribute(FACTORY);
        EntitySpec<? extends WebAppService> webServerSpec = getAttribute(MEMBER_SPEC);
        if (webServerFactory == null && webServerSpec == null) {
            log.debug("creating default web server spec for {}", this);
            webServerSpec = EntitySpec.create(JBoss7Server.class);
            setAttribute(MEMBER_SPEC, webServerSpec);
        }
        
        log.debug("creating cluster child for {}", this);
        // Note relies on initial_size being inherited by DynamicWebAppCluster, because key id is identical
        EntitySpec<? extends DynamicWebAppCluster> webClusterSpec = getAttribute(WEB_CLUSTER_SPEC);
        Map<String,Object> webClusterFlags;
        if (webServerSpec != null) {
            webClusterFlags = MutableMap.<String,Object>of("memberSpec", webServerSpec);
        } else {
            webClusterFlags = MutableMap.<String,Object>of("factory", webServerFactory);
        }
        if (webClusterSpec == null) {
            log.debug("creating default web cluster spec for {}", this);
            webClusterSpec = EntitySpec.create(DynamicWebAppCluster.class);
        }
        boolean hasMemberSpec = webClusterSpec.getConfig().containsKey(DynamicWebAppCluster.MEMBER_SPEC) || webClusterSpec.getFlags().containsKey("memberSpec");
        boolean hasMemberFactory = webClusterSpec.getConfig().containsKey(DynamicWebAppCluster.FACTORY) || webClusterSpec.getFlags().containsKey("factory");
        if (!(hasMemberSpec || hasMemberFactory)) {
            webClusterSpec.configure(webClusterFlags);
        } else {
            log.warn("In {}, not setting cluster's {} because already set on webClusterSpec", new Object[] {this, webClusterFlags.keySet()});
        }
        setAttribute(WEB_CLUSTER_SPEC, webClusterSpec);
        
        DynamicWebAppCluster cluster = addChild(webClusterSpec);
        if (Entities.isManaged(this)) Entities.manage(cluster);
        setAttribute(CLUSTER, cluster);
        
        LoadBalancer controller = getAttribute(CONTROLLER);
        if (controller == null) {
            EntitySpec<? extends LoadBalancer> controllerSpec = getAttribute(CONTROLLER_SPEC);
            if (controllerSpec == null) {
                log.debug("creating controller using default spec for {}", this);
                controllerSpec = EntitySpec.create(NginxController.class);
                setAttribute(CONTROLLER_SPEC, controllerSpec);
            } else {
                log.debug("creating controller using custom spec for {}", this);
            }
            controller = addChild(controllerSpec);
            if (Entities.isManaged(this)) Entities.manage(controller);
            setAttribute(CONTROLLER, controller);
        }
    }
    
    public LoadBalancer getController() {
        return getAttribute(CONTROLLER);
    }

    public synchronized ConfigurableEntityFactory<WebAppService> getFactory() {
        return (ConfigurableEntityFactory<WebAppService>) getAttribute(FACTORY);
    }
    
    // TODO convert to an entity reference which is serializable
    public synchronized DynamicWebAppCluster getCluster() {
        return getAttribute(CLUSTER);
    }
    
    public void start(Collection<? extends Location> locations) {
        if (isLegacyConstruction()) {
            init();
        }
        
        if (locations.isEmpty()) locations = this.getLocations();
        addLocations(locations);
        
        LoadBalancer loadBalancer = getController();
        loadBalancer.bind(MutableMap.of("serverPool", getCluster()));

        List<Entity> childrenToStart = MutableList.<Entity>of(getCluster());
        // Set controller as child of cluster, if it does not already have a parent
        if (getController().getParent() == null) {
            addChild(getController());
        }
        
        // And only start controller if we are parent
        if (this.equals(getController().getParent())) childrenToStart.add(getController());
        
        try {
            Entities.invokeEffectorList(this, childrenToStart, Startable.START, ImmutableMap.of("locations", locations))
                .get();
            
            // wait for everything to start, then update controller, to ensure it is up to date
            // (will happen asynchronously as members come online, but we want to force it to happen)
            getController().update();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } catch (ExecutionException e) {
            throw Exceptions.propagate(e);
        }
        
        connectSensors();
    }

    @Override
    public void stop() {
        List<Startable> tostop = Lists.newArrayList();
        if (this.equals(getController().getParent())) tostop.add(getController());
        tostop.add(getCluster());
        
        StartableMethods.stopSequentially(tostop);

        clearLocations();
        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void restart() {
        // TODO prod the entities themselves to restart, instead?
        Collection<Location> locations = Lists.newArrayList(getLocations());

        stop();
        start(locations);
    }
    
    void connectSensors() {
        addEnricher(Enrichers.builder()
                .propagatingAllBut(SERVICE_UP, ROOT_URL)
                .from(getCluster())
                .build());
        addEnricher(Enrichers.builder()
                // include hostname and address of controller (need both in case hostname only resolves to internal/private ip)
                .propagating(LoadBalancer.HOSTNAME, Attributes.ADDRESS, SERVICE_UP, ROOT_URL)
                .from(getController())
                .build());
    }

    public Integer resize(Integer desiredSize) {
        return getCluster().resize(desiredSize);
    }

    /**
     * @return the current size of the group.
     */
    public Integer getCurrentSize() {
        return getCluster().getCurrentSize();
    }

    private Entity findChildOrNull(Predicate<? super Entity> predicate) {
        for (Entity contender : getChildren()) {
            if (predicate.apply(contender)) return contender;
        }
        return null;
    }
}
