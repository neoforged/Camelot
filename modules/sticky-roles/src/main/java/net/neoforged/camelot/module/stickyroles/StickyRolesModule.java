
package net.neoforged.camelot.module.stickyroles;

import com.google.auto.service.AutoService;

import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.config.module.StickyRoles;
import net.neoforged.camelot.module.BuiltInModule;
import net.neoforged.camelot.module.api.CamelotModule;

@AutoService(CamelotModule.class)
public class StickyRolesModule extends CamelotModule.WithDatabase<StickyRoles> {
    public StickyRolesModule() {
        super(StickyRoles.class);
        accept(BuiltInModule.CONFIGURATION_COMMANDS, builder ->
                builder.accept(new StickyRolesConfigCommand(db().onDemand(StickyRolesDAO.class))));
    }

    @Override
    public void setup(JDA jda) {
        jda.addEventListener(new StickyRolesListener(db()));
    }

    @Override
    public String id() {
        return "sticky-roles";
    }
}
