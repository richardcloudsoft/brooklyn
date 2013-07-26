package brooklyn.entity.windows;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

public class WebpiApplicationEc2LiveTest {

    // FIXME Currently have just focused on test_Debian_6; need to test the others as well!

    // TODO No nice fedora VMs

    // TODO Instead of this sub-classing approach, we could use testng's "provides" mechanism
    // to say what combo of provider/region/flags should be used. The problem with that is the
    // IDE integration: one can't just select a single test to run.

    public static final String PROVIDER = "aws-ec2";
    public static final String REGION_NAME = "eu-west-1";
    //public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String LOCATION_SPEC = "named:richardwindows";
    public static final String TINY_HARDWARE_ID = "t1.micro";
    public static final String SMALL_HARDWARE_ID = "m1.small";
    public static final String MEDIUM_HARDWARE_ID = "m1.medium";

    protected BrooklynProperties brooklynProperties;
    protected ManagementContext ctx;

    protected TestApplication app;
    protected Location jcloudsLocation;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.remove("brooklyn.jclouds." + PROVIDER + ".image-description-regex");
        brooklynProperties.remove("brooklyn.jclouds." + PROVIDER + ".image-name-regex");
        brooklynProperties.remove("brooklyn.jclouds." + PROVIDER + ".image-id");
        brooklynProperties.remove("brooklyn.jclouds." + PROVIDER + ".inboundPorts");
        brooklynProperties.remove("brooklyn.jclouds." + PROVIDER + ".hardware-id");

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");

        ctx = new LocalManagementContext(brooklynProperties);
        app = ApplicationBuilder.newManagedApp(TestApplication.class, ctx);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }

    @Test(groups = {"Live"})
    public void test_Windows() throws Exception {
        runTest(ImmutableMap.of("imageId", "eu-west-1/ami-1d978b69", "loginUser", "Administrator", "hardwareId", MEDIUM_HARDWARE_ID));
    }

    protected void runTest(Map<?, ?> flags) throws Exception {
        Map<?, ?> jcloudsFlags = MutableMap.builder()
                .putAll(flags)
                .build();
        jcloudsLocation = ctx.getLocationRegistry().resolve(LOCATION_SPEC, jcloudsFlags);

        doTest(jcloudsLocation);
    }

    protected void doTest(Location loc) throws Exception {
        Set<String> answers = ImmutableSet.<String>builder()
                .add("AppPath[@]Default Web Site")
                .add("DbServer[@]localhost")
                .add("DbName[@]wordpress42")
                .add("DbUserName[@]wordpressuser42")
                .add("DbPassword[@]secret")
                .add("DbAdmin[@]root")
                .add("DbAdminPassword[@]secret")
                .build();
        WebpiApplication webapp = app.createAndManageChild(EntitySpecs.spec(WebpiApplication.class)
                .configure(WebpiApplication.APPLICATION_NAME, "Wordpress")
                .configure(WebpiApplication.MYSQL_ROOT_PASSWORD, "secret")
                .configure(WebpiApplication.ANSWERS, answers)
        );
        app.start(ImmutableList.of(loc));
    }
}
