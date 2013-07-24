package brooklyn.util.internal.ssh.sshj;

import brooklyn.util.internal.ssh.BashHelper;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.ssh.CommonCommands;
import brooklyn.util.text.Identifiers;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public enum SshjToolBashHelper {

    INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(SshjToolBashHelper.class);

    public int execScript(SshTool tool, Map<String,?> props, List<String> commands) {
        return execScript(tool, props, commands, Collections.<String,Object>emptyMap());
    }
    
    /**
     * This creates a script containing the user's commands, copies it to the remote server, and
     * executes the script. The script is then deleted.
     * <p>
     * Executing commands directly is fraught with dangers! Here are other options, and their problems:
     * <ul>
     *   <li>Use execCommands, rather than shell.
     *       The user's environment will not be setup normally (e.g. ~/.bash_profile will not have been sourced)
     *       so things like wget may not be on the PATH.
     *   <li>Send the stream of commands to the shell.
     *       But characters being sent can be lost.
     *       Try the following (e.g. in an OS X terminal):
     *        - sleep 5
     *        - <paste a command that is 1000s of characters long>
     *       Only the first 1024 characters appear. The rest are lost.
     *       If sending a stream of commands, you need to be careful not send the next (big) command while the
     *       previous one is still executing.
     *   <li>Send a stream to the shell, but spot when the previous command has completed.
     *       e.g. by looking for the prompt (but what if the commands being executed change the prompt?)
     *       e.g. by putting every second command as "echo <uid>", and waiting for the stdout.
     *       This gets fiddly...
     * </ul>
     * 
     * So on balance, the script-based approach seems most reliable, even if there is an overhead
     * of separate message(s) for copying the file!
     */
    public int execScript(SshTool tool, Map<String,?> props, List<String> commands, Map<String,?> env) {
        String scriptDir = BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_SCRIPT_DIR);
        Boolean noExtraOutput = BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_NO_EXTRA_OUTPUT);
        Boolean runAsRoot = BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_RUN_AS_ROOT);
        
        String scriptPath = scriptDir+"/brooklyn-"+System.currentTimeMillis()+"-"+ Identifiers.makeRandomId(8)+".sh";
        
        String scriptContents = BashHelper.INSTANCE.toScript(props, commands, env);
        
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} as script: {}", tool.getHost(), scriptContents);
        
        tool.copyToServer(ImmutableMap.of("permissions", "0700"), scriptContents.getBytes(), scriptPath);
        
        // use "-f" because some systems have "rm" aliased to "rm -i"; use "< /dev/null" to guarantee doesn't hang
        ImmutableList.Builder<String> cmds = ImmutableList.<String>builder()
                .add((runAsRoot ? CommonCommands.sudo(scriptPath) : scriptPath) + " < /dev/null")
                .add("RESULT=$?");
        if (noExtraOutput==null || !noExtraOutput)
            cmds.add("echo Executed "+scriptPath+", result $RESULT"); 
        cmds.add("rm -f "+scriptPath+" < /dev/null"); 
        cmds.add("exit $RESULT");

        return tool.executeInteractive(props, cmds.build());
    }

    public int execShellDirect(SshTool tool, Map<String,?> props, List<String> commands, Map<String,?> env) {
        List<String> cmdSequence = BashHelper.INSTANCE.toCommandSequence(commands, env);
        List<String> allcmds = ImmutableList.<String>builder()
                .add(BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_DIRECT_HEADER))
                .addAll(cmdSequence)
                .add("exit $?")
                .build();

        return tool.executeInteractive(props, allcmds);
    }

    public int execCommands(SshTool tool, Map<String,?> props, List<String> commands) {
        return execCommands(tool, props, commands, Collections.<String, Object>emptyMap());
    }

    public int execCommands(SshTool tool, Map<String,?> props, List<String> commands, Map<String,?> env) {
        if (props.containsKey("blocks") && props.get("blocks") == Boolean.FALSE) {
            throw new IllegalArgumentException("Cannot exec non-blocking: command="+commands);
        }
        String separator = BashHelper.INSTANCE.getOptionalVal(props, SshTool.PROP_SEPARATOR);

        List<String> allcmds = BashHelper.INSTANCE.toCommandSequence(commands, env);
        String singlecmd = Joiner.on(separator).join(allcmds);

        return tool.executeCommand(props, singlecmd);
    }

}
