package brooklyn.util.internal.ssh;

import com.google.common.collect.ImmutableMap;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

public enum PowerShellTranslator implements SshToolBashHelper {

    INSTANCE;

    @Override
    public int execScript(SshTool ssh, Map<String, ?> props, List<String> commands, Map<String, ?> env) {
        return execCommands(ssh, props, commands, env);
    }

    @Override
    public int execScript(SshTool ssh, Map<String, ?> props, List<String> commands) {
        return execScript(ssh, props, commands, ImmutableMap.<String, Object>of());
    }

    @Override
    public int execCommands(SshTool ssh, Map<String, ?> properties, List<String> commands, Map<String, ?> env) {
        StringBuilder formattedCommands = new StringBuilder();
        for (String command : commands) {
            formattedCommands.append(command);
            formattedCommands.append('\r');
            formattedCommands.append('\n');
        }
        formattedCommands.append("exit\r\n");
        byte[] bytes;
        try {
            bytes = formattedCommands.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            bytes = formattedCommands.toString().getBytes();
        }
        ByteArrayInputStream commandStream = new ByteArrayInputStream(bytes);
        Map<String, ?> newFlags = ImmutableMap.<String, Object>builder()
                .putAll(properties)
                .put("in", commandStream)
                .build();
        return ssh.executeCommand(newFlags, "PowerShell -Command -");
    }

    @Override
    public int execCommands(SshTool ssh, Map<String, ?> properties, List<String> commands) {
        return execCommands(ssh, properties, commands, ImmutableMap.<String, Object>of());
    }
}
