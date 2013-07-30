package brooklyn.entity.windows;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.internal.ssh.SshTool;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ChefApplicationSshDriver extends AbstractSoftwareProcessSshDriver implements ChefApplicationDriver {

    private static final ImmutableMap<String, Object> scriptFlags = ImmutableMap.<String, Object>of("nonStandardLayout", Boolean.TRUE);

    public ChefApplicationSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    private boolean isRunning = false;

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void stop() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void install() {
        String remoteHomeDirectory = getRemoteHomeDirectory();

        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = new LinkedList<String>();
        commands.add("$webclient = ( New-Object Net.WebClient )");
        commands.add("$saveas = ( [System.IO.Path]::Combine($env:USERPROFILE, \"" + saveAs + "\") )");
        commands.add("echo \"Downloading\"");
        commands.add("$webclient.DownloadFile(\"" + Iterables.get(urls, 1) + "\", $saveas)");
        commands.add("echo \"Installing\"");
        commands.add("Start-Process -FilePath \"MsiExec.exe\" -ArgumentList @( \"/quiet\", \"/qn\", \"/i\", $saveas ) -Wait -LoadUserProfile -NoNewWindow");

        String cookbooksZipFile = entity.getConfig(ChefApplication.COOKBOOKS_ZIPFILE);
        if (!Strings.isNullOrEmpty(cookbooksZipFile)) {
            getMachine().copyTo(new File(cookbooksZipFile), remoteHomeDirectory + "\\chef-config.zip");
            commands.add("echo \"Unpacking cookbooks\"");
            commands.add("$shell_app=new-object -com shell.application");
            commands.add("$zip_file = $shell_app.namespace([System.IO.Path]::Combine($env:USERPROFILE, \"chef-config.zip\"))");
            commands.add("$destination = $shell_app.namespace($env:USERPROFILE)");
            commands.add("$destination.Copyhere($zip_file.items())");
        }
//        getMachine().copyTo(new File("/Users/richard/Downloads/jdk-7u25-windows-x64.exe"), remoteHomeDirectory + "\\jdk-7u25-windows-x64.exe");
        getMachine().copyTo(new StringReader("root = File.absolute_path(File.dirname(__FILE__))\r\n" +
                "file_cache_path root\r\n" +
                "cookbook_path root + '/cookbooks'\r\n"), remoteHomeDirectory + "\\solo.rb");
        getMachine().copyTo(new StringReader("{\r\n" +
                "    \"run_list\": [ \"recipe[sql_server::server]\" ],\r\n" +
                "    \"java\" : { \"windows\" : { \"url\" : \"" + (remoteHomeDirectory + "\\jdk-7u25-windows-x64.exe\"").replace("\\", "\\\\") + " } },\r\n" +
                "    \"sql_server\" : { \"accept_eula\" : true },\r\n" +
                "    \"cf10\" : { \"installer\" : { \"local_file\" : \"" + (remoteHomeDirectory + "ColdFusion_10_WWEJ_win64.exe").replace("\\", "\\\\") + "\" } }\r\n" +
                "}\r\n"), remoteHomeDirectory + "\\solo.json");
        commands.add("echo Download CF10 installer");
        commands.add("Read-S3Object -BucketName richardwindows -Key \"ColdFusion_10_WWEJ_win64.exe\" -File \"ColdFusion_10_WWEJ_win64.exe\"");
        commands.add("echo \"Starting chef-solo with this configuration:\"");
        commands.add("type solo.rb");
        commands.add("echo \"and these attributes:\"");
        commands.add("type solo.json");
        commands.add("& C:\\opscode\\chef\\bin\\chef-solo.bat -c solo.rb -j solo.json");

        newScript(scriptFlags, INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }

    private String getRemoteHomeDirectory() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        getMachine().execCommands(ImmutableMap.<String, Object>of(SshTool.PROP_OUT_STREAM.getName(), stdout),
                "Discover home directory path on remote",
                ImmutableList.of("echo $env:USERPROFILE"));
        String response = stdout.toString();
        return response.trim();
    }

    @Override
    public void customize() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void launch() {
        isRunning = true;
    }
}
