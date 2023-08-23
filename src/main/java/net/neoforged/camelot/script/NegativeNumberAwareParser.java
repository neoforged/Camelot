package net.neoforged.camelot.script;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

/**
 * A {@link CmdLineParser} that does not consider negative numbers options.
 */
public class NegativeNumberAwareParser extends CmdLineParser {

    public NegativeNumberAwareParser(Object bean, ParserProperties parserProperties) {
        super(bean, parserProperties);
    }

    @Override
    protected boolean isOption(String arg) {
        final boolean isSuperOption = super.isOption(arg);
        if (isSuperOption && arg.chars().allMatch(p -> p == '-' || (p >= '0' && p <= '9'))) {
            return false;
        }
        return isSuperOption;
    }
}
