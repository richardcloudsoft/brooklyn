package brooklyn.entity.proxy;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class AbstractNonProvisionedControllerImpl extends AbstractEntity implements AbstractNonProvisionedController {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNonProvisionedControllerImpl.class);
    
    protected volatile boolean isActive;
    protected volatile boolean updateNeeded = true;
    
    protected AbstractMembershipTrackingPolicy serverPoolMemberTrackerPolicy;
    protected Set<String> serverPoolAddresses = Sets.newLinkedHashSet();
    protected Map<Entity,String> serverPoolTargets = Maps.newLinkedHashMap();
    
    public AbstractNonProvisionedControllerImpl() {
        this(MutableMap.of(), null, null);
    }
    public AbstractNonProvisionedControllerImpl(Map properties) {
        this(properties, null, null);
    }
    public AbstractNonProvisionedControllerImpl(Entity parent) {
        this(MutableMap.of(), parent, null);
    }
    public AbstractNonProvisionedControllerImpl(Map properties, Entity parent) {
        this(properties, parent, null);
    }
    public AbstractNonProvisionedControllerImpl(Entity parent, Cluster cluster) {
        this(MutableMap.of(), parent, cluster);
    }
    public AbstractNonProvisionedControllerImpl(Map properties, Entity parent, Cluster cluster) {
        serverPoolMemberTrackerPolicy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Controller targets tracker")) {
            protected void onEntityChange(Entity member) { onServerPoolMemberChanged(member); }
            protected void onEntityAdded(Entity member) { onServerPoolMemberChanged(member); }
            protected void onEntityRemoved(Entity member) { onServerPoolMemberChanged(member); }
        };
    }
    
    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'serverPool'.
     */
    @Override
    public void bind(Map flags) {
        if (flags.containsKey("serverPool")) {
            setConfigEvenIfOwned(SERVER_POOL, (Group) flags.get("serverPool"));
        }
    }

    @Override
    public boolean isActive() {
        return isActive;
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        preStart();
    }

    protected void preStart() {
        AttributeSensor<?> hostAndPortSensor = getConfig(HOST_AND_PORT_SENSOR);
        Maybe<Object> hostnameSensor = getConfigRaw(HOSTNAME_SENSOR, true);
        Maybe<Object> portSensor = getConfigRaw(PORT_NUMBER_SENSOR, true);
        if (hostAndPortSensor != null) {
            checkState(!hostnameSensor.isPresent() && !portSensor.isPresent(), 
                    "Must not set %s and either of %s or %s", HOST_AND_PORT_SENSOR, HOSTNAME_SENSOR, PORT_NUMBER_SENSOR);
        }
        
        ConfigToAttributes.apply(this);
        LOG.info("Adding policy {} to {}, during start", serverPoolMemberTrackerPolicy, this);
        addPolicy(serverPoolMemberTrackerPolicy);
        resetServerPoolMemberTrackerPolicy();
    }
    
    protected synchronized void resetServerPoolMemberTrackerPolicy() {
        serverPoolMemberTrackerPolicy.reset();
        serverPoolAddresses.clear();
        serverPoolTargets.clear();
        if (groovyTruth(getServerPool())) {
            serverPoolMemberTrackerPolicy.setGroup(getServerPool());
            
            // Initialize ourselves immediately with the latest set of members; don't wait for
            // listener notifications because then will be out-of-date for short period (causing 
            // problems for rebind)
            for (Entity member : getServerPool().getMembers()) {
                if (belongsInServerPool(member)) {
                    if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
                    String address = getAddressOfEntity(member);
                    serverPoolTargets.put(member, address);
                    if (address != null) {
                        serverPoolAddresses.add(address);
                    }
                }
            }
            
            LOG.info("Resetting {}, members {} with address {}", new Object[] {this, serverPoolTargets, serverPoolAddresses});
        }
        
        setAttribute(SERVER_POOL_TARGETS, serverPoolAddresses);
    }
    
    /** 
     * Implementations should update the configuration so that 'serverPoolAddresses' are targeted.
     * The caller will subsequently call reload to apply the new configuration.
     */
    protected abstract void reconfigureService();
    
    @Override
    public synchronized void update() {
        if (!isActive()) updateNeeded = true;
        else {
            updateNeeded = false;
            LOG.debug("Updating {} in response to changes", this);
            reconfigureService();
            LOG.debug("Reloading {} in response to changes", this);
            invoke(RELOAD);
        }
        setAttribute(SERVER_POOL_TARGETS, serverPoolAddresses);
    }
    
    protected synchronized void onServerPoolMemberChanged(Entity member) {
        if (LOG.isTraceEnabled()) LOG.trace("For {}, considering membership of {} which is in locations {}", 
                new Object[] {this, member, member.getLocations()});
        if (belongsInServerPool(member)) {
            addServerPoolMember(member);
        } else {
            removeServerPoolMember(member);
        }
        if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
    }
    
    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not up", this, member);
            return false;
        }
        if (!getServerPool().getMembers().contains(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not member", this, member);
            return false;
        }
        if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, approving", this, member);
        return true;
    }
    
    private Group getServerPool() {
        return getConfig(SERVER_POOL);
    }
    
    protected AttributeSensor<Integer> getPortNumberSensor() {
        return getAttribute(PORT_NUMBER_SENSOR);
    }
    
    protected AttributeSensor<String> getHostnameSensor() {
        return getAttribute(HOSTNAME_SENSOR);
    }

    protected AttributeSensor<String> getHostAndPortSensor() {
        return getAttribute(HOST_AND_PORT_SENSOR);
    }

    protected synchronized void addServerPoolMember(Entity member) {
        if (serverPoolTargets.containsKey(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("For {}, not adding as already have member {}", new Object[] {this, member});
            return;
        }
        
        String address = getAddressOfEntity(member);
        if (address != null) {
            serverPoolAddresses.add(address);
        }

        LOG.info("Adding to {}, new member {} with address {}", new Object[] {this, member, address});
        
        update();
        serverPoolTargets.put(member, address);
    }
    
    protected synchronized void removeServerPoolMember(Entity member) {
        if (!serverPoolTargets.containsKey(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("For {}, not removing as don't have member {}", new Object[] {this, member});
            return;
        }
        
        String address = serverPoolTargets.get(member);
        if (address != null) {
            serverPoolAddresses.remove(address);
        }
        
        LOG.info("Removing from {}, member {} with address {}", new Object[] {this, member, address});
        
        update();
        serverPoolTargets.remove(member);
    }
    
    protected String getAddressOfEntity(Entity member) {
        AttributeSensor<String> hostAndPortSensor = getHostAndPortSensor();
        if (hostAndPortSensor != null) {
            String result = member.getAttribute(hostAndPortSensor);
            if (result != null) {
                return result;
            } else {
                LOG.error("No host:port set for {} (using attribute {}); skipping in {}", 
                        new Object[] {member, hostAndPortSensor, this});
                return null;
            }
        } else {
            String ip = member.getAttribute(getHostnameSensor());
            Integer port = member.getAttribute(getPortNumberSensor());
            if (ip!=null && port!=null) {
                return ip+":"+port;
            }
            LOG.error("Unable to construct hostname:port representation for {} ({}:{}); skipping in {}", 
                    new Object[] {member, ip, port, this});
            return null;
        }
    }
}
