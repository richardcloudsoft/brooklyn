package brooklyn.util.internal.ssh;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: richard
 * Date: 24/07/2013
 * Time: 15:16
 * To change this template use File | Settings | File Templates.
 */
public interface SshToolBashHelper {
    /**
     * Executes the set of commands in a shell script. Blocks until completion.
     * <p>
     *
     * Optional properties are:
     * <ul>
     *   <li>'out' {@link java.io.OutputStream} - see {@link PROP_OUT_STREAM}
     *   <li>'err' {@link java.io.OutputStream} - see {@link PROP_ERR_STREAM}
     * </ul>
     *
     * @return exit status of script
     *
     * @throws brooklyn.util.internal.ssh.SshException If failed to connect
     */
    int execScript(SshTool tool, Map<String, ?> props, List<String> commands, Map<String, ?> env);

    /**
     * @see execScript( java.util.Map, java.util.List, java.util.Map)
     */
    int execScript(SshTool tool, Map<String, ?> props, List<String> commands);

    /**
     * Executes the set of commands using ssh exec.
     *
     * This is generally more efficient than shell, but is not suitable if you need
     * env values which are only set on a fully-fledged shell.
     *
     * Optional properties are:
     * <ul>
     *   <li>'out' {@link java.io.OutputStream} - see {@link PROP_OUT_STREAM}
     *   <li>'err' {@link java.io.OutputStream} - see {@link PROP_ERR_STREAM}
     *   <li>'separator', defaulting to ";" - see {@link PROP_SEPARATOR}
     * </ul>
     *
     * @return exit status of commands
     * @throws brooklyn.util.internal.ssh.SshException If failed to connect
     */
    int execCommands(SshTool tool, Map<String, ?> properties, List<String> commands, Map<String, ?> env);

    /**
     * @see execuCommands( java.util.Map, java.util.List, java.util.Map)
     */
    int execCommands(SshTool tool, Map<String, ?> properties, List<String> commands);
}
