package brooklyn.util.internal.ssh.cli;

import brooklyn.config.ConfigKey;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.ssh.BashHelper;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.SshToolBashHelper;
import brooklyn.util.ssh.CommonCommands;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringEscapes;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public enum SshCliToolBashHelper implements SshToolBashHelper {

    INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(SshCliToolBashHelper.class);
    private final File localTempDir = new File(System.getProperty("java.io.tmpdir"), "tmpssh");

    public int execScript(SshTool tool, Map<String,?> props, List<String> commands) {
        return execScript(tool, props, commands, Collections.<String, Object>emptyMap());
    }

    public int execScript(SshTool tool, Map<String,?> props, List<String> commands, Map<String,?> env) {
        String separator = BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_SEPARATOR);
        String scriptDir = BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_SCRIPT_DIR);
        Boolean runAsRoot = BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_RUN_AS_ROOT);
        Boolean noExtraOutput = BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_NO_EXTRA_OUTPUT);
        String scriptPath = scriptDir+"/brooklyn-"+System.currentTimeMillis()+"-"+ Identifiers.makeRandomId(8)+".sh";

        String scriptContents = BashHelper.INSTANCE.toScript(props, commands, env);

        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} as script: {}", tool.getHost(), scriptContents);

        File f = writeTempFile(scriptContents);
        try {
            tool.copyToServer(ImmutableMap.of("permissions", "0700"), f, scriptPath);
        } finally {
            f.delete();
        }

        // use "-f" because some systems have "rm" aliased to "rm -i"; use "< /dev/null" to guarantee doesn't hang
        String cmd =
                (runAsRoot ? CommonCommands.sudo(scriptPath) : scriptPath) + " < /dev/null"+separator+
                "RESULT=\\$?"+separator+
                (noExtraOutput==null || !noExtraOutput ? "echo Executed "+scriptPath+", result \\$RESULT"+separator : "")+
                "rm -f "+scriptPath+" < /dev/null"+separator+
                "exit \\$RESULT";
        Integer result = tool.executeCommand(props, "bash -c \"" + cmd + "\"");
        return result != null ? result : -1;
    }

    public int execCommands(SshTool tool, Map<String,?> props, List<String> commands) {
        return execCommands(tool, props, commands, Collections.<String,Object>emptyMap());
    }

    public int execCommands(SshTool tool, Map<String,?> props, List<String> commands, Map<String,?> env) {
        Map<String,Object> props2 = new MutableMap<String,Object>();
        if (props!=null) props2.putAll(props);
        props2.put(SshTool.PROP_NO_EXTRA_OUTPUT.getName(), true);
        return execScript(tool, props2, commands, env);
    }

    protected File writeTempFile(String string) {
        byte[] bytes = string.getBytes();
        InputStream contents = new ByteArrayInputStream(bytes);
        // TODO Use ConfigKeys.BROOKLYN_DATA_DIR, but how to get access to that here?
        File tempFile = ResourceUtils.writeToTempFile(contents, localTempDir, "sshcopy", "data");
        tempFile.setReadable(false, false);
        tempFile.setReadable(true, true);
        tempFile.setWritable(false);
        tempFile.setExecutable(false);
        return tempFile;
    }

}
