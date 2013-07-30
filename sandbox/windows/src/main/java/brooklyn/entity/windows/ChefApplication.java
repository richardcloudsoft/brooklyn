package brooklyn.entity.windows;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name = "Chef",
        description = "Chef is an automation platform that transforms infrastructure into code",
        iconUrl = "classpath:///chef-logo.png")
@ImplementedBy(ChefApplicationImpl.class)
public interface ChefApplication extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "11.6.0");

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://opscode.com/chef/install.msi");

    @SetFromFlag("cookbooks.zipfile")
    ConfigKey<String> COOKBOOKS_ZIPFILE =
            new BasicConfigKey<String>(String.class, "cookbooks.zipfile", null);

}
