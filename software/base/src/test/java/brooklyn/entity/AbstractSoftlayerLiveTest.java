package brooklyn.entity;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * Runs a test with many different distros and versions.
 */
public abstract class AbstractSoftlayerLiveTest {
    
    public static final String PROVIDER = "softlayer";

    protected BrooklynProperties brooklynProperties;
    protected ManagementContext ctx;
    
    protected TestApplication app;
    protected Location jcloudsLocation;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        List<String> propsToRemove = ImmutableList.of("imageId", "imageDescriptionRegex", "imageNameRegex", "inboundPorts", "hardwareId", "minRam");

        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        for (String propToRemove : propsToRemove) {
            for (String propVariant : ImmutableList.of(propToRemove, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, propToRemove))) {
                brooklynProperties.remove("brooklyn.locations.jclouds."+PROVIDER+"."+propVariant);
                brooklynProperties.remove("brooklyn.locations."+propVariant);
                brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+"."+propVariant);
                brooklynProperties.remove("brooklyn.jclouds."+propVariant);
            }
        }

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");
        
        ctx = new LocalManagementContext(brooklynProperties);
        app = ApplicationBuilder.newManagedApp(TestApplication.class, ctx);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = {"Live"})
    public void test_Default() throws Exception {
        runTest(ImmutableMap.<String,Object>of());
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_12_0_4() throws Exception {
        // Image: {id=UBUNTU_12_64, providerId=UBUNTU_12_64, os={family=ubuntu, version=12.04, description=Ubuntu / Ubuntu / 12.04.0-64 Minimal, is64Bit=true}, description=UBUNTU_12_64, status=AVAILABLE, loginUser=root}
        runTest(ImmutableMap.<String,Object>of("imageId", "UBUNTU_12_64"));
    }

    @Test(groups = {"Live"})
    public void test_Centos_6_0() throws Exception {
      // Image: {id=CENTOS_6_64, providerId=CENTOS_6_64, os={family=centos, version=6.5, description=CentOS / CentOS / 6.5-64 LAMP for Bare Metal, is64Bit=true}, description=CENTOS_6_64, status=AVAILABLE, loginUser=root}
        runTest(ImmutableMap.<String,Object>of("imageId", "CENTOS_^_64"));
    }
    
    protected void runTest(Map<String,?> flags) throws Exception {
        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(getClass().getName()))
                .put("vmNameMaxLength", 30)
                .putAll(flags)
                .build();
        jcloudsLocation = ctx.getLocationRegistry().resolve(PROVIDER, allFlags);

        doTest(jcloudsLocation);
    }
    
    protected abstract void doTest(Location loc) throws Exception;
}
