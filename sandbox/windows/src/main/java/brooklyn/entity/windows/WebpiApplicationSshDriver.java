package brooklyn.entity.windows;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.basic.SshMachineLocation;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class WebpiApplicationSshDriver extends AbstractSoftwareProcessSshDriver implements WebpiApplicationDriver {

    private static final ImmutableMap<String, Object> scriptFlags = ImmutableMap.<String, Object>of("nonStandardLayout", Boolean.TRUE);

    public WebpiApplicationSshDriver(EntityLocal entity, SshMachineLocation machine) {
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
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = new LinkedList<String>();
        commands.add("$webclient = ( New-Object Net.WebClient )");
        commands.add("$saveas = ( [System.IO.Path]::Combine($env:USERPROFILE, \"" + saveAs + "\") )");
        commands.add("echo Saving to $saveas");
        commands.add("$webclient.DownloadFile(\"" + Iterables.get(urls, 1) + "\", $saveas)");
        commands.add("echo Start-Process -FilePath \"MsiExec.exe\" -ArgumentList @( \"/quiet\", \"/qn\", \"/i\", $saveas ) -Wait -LoadUserProfile -NoNewWindow");
        commands.add("Start-Process -FilePath \"MsiExec.exe\" -ArgumentList @( \"/quiet\", \"/qn\", \"/i\", $saveas ) -Wait -LoadUserProfile -NoNewWindow");

        newScript(scriptFlags, INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }

    @Override
    public void customize() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void launch() {
        List<String> commands = new LinkedList<String>();

        // Build the answer file
        Set<String> answers = entity.getConfig(WebpiApplication.ANSWERS);
        boolean answerFileInUse = answers != null && !answers.isEmpty();
        if (answerFileInUse) {
            boolean first = true;
            for (String answer : answers) {
                commands.add(String.format("%s-Content -Path \"answerfile\" -Value \"%s\"", first ? "Set" : "Add", answer));
                first = false;
            }
        }

        // Build the install command
        String appArg = "/Application:" + entity.getConfig(WebpiApplication.APPLICATION_NAME);
        if (answerFileInUse)
            appArg += "@" + "answerfile";

        ImmutableList.Builder<String> argsBuilder = ImmutableList.<String>builder()
                .add("/Install")
                .add(appArg)
                .add("/AcceptEula");

        Set<String> customFeeds = entity.getConfig(WebpiApplication.CUSTOM_FEEDS);
        if (customFeeds != null && !customFeeds.isEmpty())
            argsBuilder.add("/Feeds:" + Joiner.on(',').join(customFeeds));

        String languageId = entity.getConfig(WebpiApplication.LANGUAGE_ID);
        if (!Strings.isNullOrEmpty(languageId))
            argsBuilder.add("/Language:" + languageId);

        String sqlServerPassword = entity.getConfig(WebpiApplication.SQLSERVER_SA_PASSWORD);
        if (!Strings.isNullOrEmpty(sqlServerPassword))
            argsBuilder.add("/SQLPassword:" + sqlServerPassword);

        String mySqlPassword = entity.getConfig(WebpiApplication.MYSQL_ROOT_PASSWORD);
        if (!Strings.isNullOrEmpty(mySqlPassword))
            argsBuilder.add("/MySQLPassword:" + mySqlPassword);

        String args = "\"" + Joiner.on("\", \"").join(argsBuilder.build()) + "\"";
        String command = String.format("Start-Process -FilePath ( Join-Path -Path $ENV:ProgramFiles -ChildPath \"Microsoft\\Web Platform Installer\\WebpiCmd.exe\" ) -ArgumentList @( %s ) -Wait -NoNewWindow", args);
        commands.add(command);
        log.info(command);

        newScript(scriptFlags, LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(commands).execute();
        isRunning = true;
    }
}
