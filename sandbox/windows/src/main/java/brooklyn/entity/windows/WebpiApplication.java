package brooklyn.entity.windows;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;
import com.google.common.reflect.TypeToken;

import java.util.Set;

@Catalog(name = "Web Platform Installer Application",
        description = "Microsoft Web Platform Installer allows easy installation of a variety of well-known web applications",
        iconUrl = "classpath:///webpi-logo.png")
@ImplementedBy(WebpiApplicationImpl.class)
public interface WebpiApplication extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "4.1");

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://download.microsoft.com/download/7/0/4/704CEB4C-9F42-4962-A2B0-5C84B0682C7A/WebPlatformInstaller_amd64_en-US.msi");

    @SetFromFlag("applicationName")
    ConfigKey<String> APPLICATION_NAME = new BasicConfigKey<String>(
            String.class, "application.name", "Name of the application to install");

    @SetFromFlag("answers")
    ConfigKey<Set<String>> ANSWERS = new BasicConfigKey<Set<String>>(
            new TypeToken<Set<String>>() {
            }, "answers", "Set of answers for installation properties");

    @SetFromFlag("customFeeds")
    ConfigKey<Set<String>> CUSTOM_FEEDS = new BasicConfigKey<Set<String>>(
            new TypeToken<Set<String>>() {
            }, "customFeeds", "list of custom XML feeds that you want to load in addition to the main WebPI feeds");

    @SetFromFlag("languageId")
    ConfigKey<String> LANGUAGE_ID = new BasicConfigKey<String>(
            String.class, "languageId", "Language of installers to be used.");

    @SetFromFlag("sqlServerPassword")
    ConfigKey<String> SQLSERVER_SA_PASSWORD = new BasicConfigKey<String>(
            String.class, "sqlserver.sa.password", "SQL Server password for 'sa' user");

    @SetFromFlag("mysqlPassword")
    ConfigKey<String> MYSQL_ROOT_PASSWORD = new BasicConfigKey<String>(
            String.class, "mysql.root.password", "MySQL password for 'root' user");

}
