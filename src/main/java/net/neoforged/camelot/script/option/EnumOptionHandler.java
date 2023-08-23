package net.neoforged.camelot.script.option;

import net.neoforged.camelot.script.ScriptOptions;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Messages;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import java.util.ResourceBundle;
import java.util.Set;

/**
 * An option handler that allows a value present in a set.
 */
public class EnumOptionHandler extends OptionHandler<String> {
    public final Set<String> enumType;

    public EnumOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super String> setter) {
        super(parser, option, setter);
        this.enumType = ((ScriptOptions.ListSetter<String>) setter).asEnum;
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
        final String parsed = params.getParameter(0).replaceAll("-", "_");
        String value = null;
        for (String e : enumType) {
            if (e.equalsIgnoreCase(parsed)) {
                value = e;
                break;
            }
        }


        if (value == null) {
            if (option.isArgument()) {
                throw new CmdLineException(owner, Messages.ILLEGAL_OPERAND, option.toString(), parsed);
            } else {
                throw new CmdLineException(owner, Messages.ILLEGAL_OPERAND, params.getParameter(-1), parsed);
            }
        }
        setter.addValue(value);
        return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
        StringBuilder rv = new StringBuilder();
        rv.append("[");
        for (String t : enumType) {
            rv.append(t).append(" | ");
        }
        rv.delete(rv.length() - 3, rv.length());
        rv.append("]");
        return rv.toString();
    }

    @Override
    public String getMetaVariable(ResourceBundle rb) {
        return getDefaultMetaVariable();
    }
}
