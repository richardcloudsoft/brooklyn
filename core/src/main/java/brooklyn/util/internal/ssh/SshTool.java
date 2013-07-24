package brooklyn.util.internal.ssh;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import static brooklyn.entity.basic.ConfigKeys.*;
import brooklyn.util.stream.KnownSizeInputStream;

/**
 * Defines the methods available on the various different implementations of SSH,
 * and configuration options which are also generally available.
 * <p>
 * The config keys in this class can be supplied (or their string equivalents, where the flags/props take Map<String,?>)
 * to influence configuration, either for the tool/session itself or for individual commands.
 * <p>
 * To specify some of these properties on a global basis, use the variants of the keys here
 * contained in {@link ConfigKeys}
 * (which are generally {@value #BROOKLYN_CONFIG_KEY_PREFIX} prefixed to the names of keys here).
 */
public interface SshTool {

//    /** Intermediate config keys for Brooklyn are defined where they are used, e.g. in {@link SshMachineLocation} 
//     * and have this prefix pre-prended to the config keys in this class. */
//    public static final String LOCATION_CONFIG_KEY_PREFIX = "ssh.config.";
    
    /** Public-facing global config keys for Brooklyn are defined in ConfigKeys, 
     * and have this prefix pre-prended to the config keys in this class. */
    public static final String BROOKLYN_CONFIG_KEY_PREFIX = "brooklyn.ssh.config.";
    
    public static final ConfigKey<String> PROP_TOOL_CLASS = newStringConfigKey("tool.class", "SshTool implementation to use", null);
    
    public static final ConfigKey<String> PROP_HOST = newStringConfigKey("host", "Host to connect to (required)", null);
    public static final ConfigKey<Integer> PROP_PORT = newConfigKey("port", "Port on host to connect to", 22);
    public static final ConfigKey<String> PROP_USER = newConfigKey("user", "User to connect as", System.getProperty("user.name"));
    public static final ConfigKey<String> PROP_PASSWORD = newStringConfigKey("password", "Password to use to connect", null);
    
    public static final ConfigKey<String> PROP_PRIVATE_KEY_FILE = newStringConfigKey("privateKeyFile", "the path of an ssh private key file; leave blank to use defaults (i.e. ~/.ssh/id_rsa and id_dsa)", null);
    public static final ConfigKey<String> PROP_PRIVATE_KEY_DATA = newStringConfigKey("privateKeyData", "the private ssh key (e.g. contents of an id_rsa.pub or id_dsa.pub file)", null);
    public static final ConfigKey<String> PROP_PRIVATE_KEY_PASSPHRASE = newStringConfigKey("privateKeyPassphrase", "the passphrase for the ssh private key", null);
    public static final ConfigKey<Boolean> PROP_STRICT_HOST_KEY_CHECKING = newConfigKey("strictHostKeyChecking", "whether to check the remote host's identification; defaults to false", false);
    public static final ConfigKey<Boolean> PROP_ALLOCATE_PTY = newConfigKey("allocatePTY", "whether to allocate PTY (vt100); if true then stderr is sent to stdout, but sometimes required for sudo'ing due to requiretty", false);

    public static final ConfigKey<Integer> PROP_CONNECT_TIMEOUT = newConfigKey("connectTimeout", "The timeout when establishing an SSH connection; if 0 then uses default", 0);
    public static final ConfigKey<Integer> PROP_SESSION_TIMEOUT = newConfigKey("sessionTimeout", "The timeout for an ssh session; if 0 then uses default", 0);
    public static final ConfigKey<Integer> PROP_SSH_TRIES = newConfigKey("sshTries", "Max number of attempts to connect when doing ssh operations", 4);
    public static final ConfigKey<Integer> PROP_SSH_TRIES_TIMEOUT = newConfigKey("sshTriesTimeout", "Timeout when attempting to connect for ssh operations; so if too slow trying sshTries times, will abort anyway", 2*60*1000);
    public static final ConfigKey<Long> PROP_SSH_RETRY_DELAY = newConfigKey("sshRetryDelay", "Time (in milliseconds) before first ssh-retry, after which it will do exponential backoff", 50L);

    public static final ConfigKey<File> PROP_LOCAL_TEMP_DIR = newConfigKey("localTempDir", "The directory on the local machine (i.e. running brooklyn) for writing temp files", 
            new File(System.getProperty("java.io.tmpdir"), "tmpssh"));
    
    // NB -- items above apply for _session_ (a tool), below apply for a _call_
    // TODO would be nice to track which arguments are used, so we can indicate whether extras are supplied

    public static final ConfigKey<Boolean> PROP_RUN_AS_ROOT = newConfigKey("runAsRoot", "When running a script, whether to run as root", Boolean.FALSE);
    
    public static final ConfigKey<OutputStream> PROP_OUT_STREAM = newConfigKey(OutputStream.class, "out", "Stream to which to capture stdout");
    public static final ConfigKey<OutputStream> PROP_ERR_STREAM = newConfigKey(OutputStream.class, "err", "Stream to which to capture stderr");
    
    public static final ConfigKey<Boolean> PROP_NO_EXTRA_OUTPUT = newConfigKey("noExtraOutput", "Suppresses any decorative output such as result code which some tool commands insert", false);
    
    public static final ConfigKey<String> PROP_SEPARATOR = newConfigKey("separator", "string to insert between caller-supplied commands being executed as commands", " ; ");
    
    public static final ConfigKey<String> PROP_SCRIPT_DIR = newConfigKey("scriptDir", "directory where scripts should be copied", "/tmp");
    public static final ConfigKey<String> PROP_SCRIPT_HEADER = newConfigKey("scriptHeader", "lines to insert at the start of scripts generated for caller-supplied commands for script execution", "#!/bin/bash -e\n");
    public static final ConfigKey<String> PROP_DIRECT_HEADER = newConfigKey("directHeader", "commands to run remotely before any caller-supplied commands for direct execution", "exec bash -e");

    public static final ConfigKey<String> PROP_PERMISSIONS = newConfigKey("permissions", "Default permissions for files copied/created on remote machine; must be four-digit octal string, default '0644'", "0644");
    public static final ConfigKey<Long> PROP_LAST_MODIFICATION_DATE = newConfigKey("lastModificationDate", "Last-modification-date to be set on files copied/created (should be UTC/1000, ie seconds since 1970; defaults to current)", 0L);
    public static final ConfigKey<Long> PROP_LAST_ACCESS_DATE = newConfigKey("lastAccessDate", "Last-access-date to be set on files copied/created (should be UTC/1000, ie seconds since 1970; defaults to lastModificationDate)", 0L);

    // TODO Could define the following in SshMachineLocation, or some such?
    //public static ConfigKey<String> PROP_LOG_PREFIX = newStringKey("logPrefix", "???", ???);
    //public static ConfigKey<Boolean> PROP_NO_STDOUT_LOGGING = newStringKey("noStdoutLogging", "???", ???);
    //public static ConfigKey<Boolean> PROP_NO_STDOUT_LOGGING = newStringKey("noStdoutLogging", "???", ???);

    /**
     * @throws SshException
     */
    public void connect();

    /**
     * @param maxAttempts
     * @throws SshException
     */
    public void connect(int maxAttempts);

    public void disconnect();

    public boolean isConnected();

    /**
     * The host the instance is connected to.
     * @return the host that this instance is connected to.
     */
    public String getHost();

    /**
     * Executes the set of commands in a shell script. Blocks until completion.
     * <p>
     * 
     * Optional properties are:
     * <ul>
     *   <li>'out' {@link OutputStream} - see {@link PROP_OUT_STREAM}
     *   <li>'err' {@link OutputStream} - see {@link PROP_ERR_STREAM}
     * </ul>
     * 
     * @return exit status of script
     * 
     * @throws SshException If failed to connect
     */
    public int execScript(Map<String,?> props, List<String> commands, Map<String,?> env);

    /**
     * @see execScript(Map, List, Map)
     */
    public int execScript(Map<String,?> props, List<String> commands);

    /**
     * Executes the set of commands using ssh exec.
     * 
     * This is generally more efficient than shell, but is not suitable if you need 
     * env values which are only set on a fully-fledged shell.
     *
     * Optional properties are:
     * <ul>
     *   <li>'out' {@link OutputStream} - see {@link PROP_OUT_STREAM}
     *   <li>'err' {@link OutputStream} - see {@link PROP_ERR_STREAM}
     *   <li>'separator', defaulting to ";" - see {@link PROP_SEPARATOR}
     * </ul>
     * 
     * @return exit status of commands
     * @throws SshException If failed to connect
     */
    public int execCommands(Map<String,?> properties, List<String> commands, Map<String,?> env);

    /**
     * @see execuCommands(Map, List, Map)
     */
    public int execCommands(Map<String,?> properties, List<String> commands);

    /**
     * Open an ssh session and send a list of strings to the remote session.
     *
     * This operation is equivalent to running "ssh" without a command, and then typing the given list of commands to
     * the input stream of the remote session. Each string in the list is followed by a newline.
     *
     * Optional properties are:
     * <ul>
     *   <li>'out' {@link OutputStream} - see {@link PROP_OUT_STREAM}
     *   <li>'err' {@link OutputStream} - see {@link PROP_ERR_STREAM}
     *   <li>'separator', defaulting to ";" - see {@link PROP_SEPARATOR}
     * </ul>
     *
     * @return exit status of commands
     * @throws SshException If failed to connect
     */
    public int executeInteractive(Map<String,?> properties, List<String> command);

    /**
     * Execute a command in an ssh session.
     *
     * This operation is equivalent to running "ssh" and passing a command as an option to the SSH command.
     *
     * Optional properties are:
     * <ul>
     *   <li>'out' {@link OutputStream} - see {@link PROP_OUT_STREAM}
     *   <li>'err' {@link OutputStream} - see {@link PROP_ERR_STREAM}
     *   <li>'separator', defaulting to ";" - see {@link PROP_SEPARATOR}
     * </ul>
     *
     * @return exit status of commands
     * @throws SshException If failed to connect
     */
    public int executeCommand(Map<String,?> properties, String command);

    /**
     * Copies the file to the server at the given path.
     * If path is null, empty, '.', '..', or ends with '/' then file name is used.
     * <p>
     * The file will not preserve the permission of last _access_ date.
     * 
     * Optional properties are:
     * <ul>
     *   <li>'permissions' (e.g. "0644") - see {@link PROP_PERMISSIONS}
     *   <li>'lastModificationDate' see {@link PROP_LAST_MODIFICATION_DATE}; not supported by all SshTool implementations
     *   <li>'lastAccessDate' see {@link PROP_LAST_ACCESS_DATE}; not supported by all SshTool implementations
     * </ul>
     * 
     * @return exit code (not supported by all SshTool implementations, sometimes just returning 0)
     */
    public int copyToServer(Map<String,?> props, File localFile, String pathAndFileOnRemoteServer);

    /**
     * Closes the given input stream before returning.
     * Consider using {@link KnownSizeInputStream} for efficiency when the size of the stream is known.
     * 
     * @see copyToServer(Map, File, String)
     */
    public int copyToServer(Map<String,?> props, InputStream contents, String pathAndFileOnRemoteServer);

    /**
     * @see copyToServer(Map, File, String)
     */
    public int copyToServer(Map<String,?> props, byte[] contents, String pathAndFileOnRemoteServer);

    /**
     * Copies the file to the server at the given path.
     * If path is null, empty, '.', '..', or ends with '/' then file name is used.
     * <p>
     * Optional properties are:
     * <ul>
     *   <li>'permissions' (e.g. "0644") - see {@link PROP_PERMISSIONS}
     * </ul>
     *
     * @return exit code (not supported by all SshTool implementations, sometimes just returning 0)
     */
    public int copyFromServer(Map<String,?> props, String pathAndFileOnRemoteServer, File local);
}
