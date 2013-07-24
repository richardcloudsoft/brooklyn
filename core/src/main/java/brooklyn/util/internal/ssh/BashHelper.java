package brooklyn.util.internal.ssh;

import brooklyn.config.ConfigKey;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.text.StringEscapes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum BashHelper {

    INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(BashHelper.class);

    /**
     * Merges the commands and env, into a single set of commands. Also escapes the commands as required.
     *
     * Not all ssh servers handle "env", so instead convert env into exported variables
     */
    public List<String> toCommandSequence(List<String> commands, Map<String,?> env) {
        List<String> result = new ArrayList<String>((env!=null ? env.size() : 0) + commands.size());

        if (env!=null) {
            for (Map.Entry<String,?> entry : env.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    LOG.warn("env key-values must not be null; ignoring: key="+entry.getKey()+"; value="+entry.getValue());
                    continue;
                }
                String escapedVal = StringEscapes.BashStringEscapes.escapeLiteralForDoubleQuotedBash(entry.getValue().toString());
                result.add("export "+entry.getKey()+"=\""+escapedVal+"\"");
            }
        }
        for (CharSequence cmd : commands) { // objects in commands can be groovy GString so can't treat as String here
            result.add(cmd.toString());
        }

        return result;
    }

    public String toScript(Map<String,?> props, List<String> commands, Map<String,?> env) {
        List<String> allcmds = toCommandSequence(commands, env);

        StringBuilder result = new StringBuilder();
        // -e causes it to fail on any command in the script which has an error (non-zero return code)
        result.append(getOptionalVal(props, SshTool.PROP_SCRIPT_HEADER)+"\n");

        for (String cmd : allcmds) {
            result.append(cmd + "\n");
        }

        return result.toString();
    }

    public static <T> T getOptionalVal(Map<String,?> map, ConfigKey<T> keyC) {
        String key = keyC.getName();
        if (map.containsKey(key)) {
            return TypeCoercions.coerce(map.get(key), keyC.getTypeToken());
        } else {
            return keyC.getDefaultValue();
        }
    }

}
