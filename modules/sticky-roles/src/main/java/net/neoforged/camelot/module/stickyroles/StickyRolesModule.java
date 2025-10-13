
package net.neoforged.camelot.module.stickyroles;

import net.dv8tion.jda.api.JDA;
import net.neoforged.camelot.ModuleProvider;
import net.neoforged.camelot.ap.RegisterCamelotModule;
import net.neoforged.camelot.config.module.StickyRoles;
import net.neoforged.camelot.module.BuiltInModule;
import net.neoforged.camelot.module.api.CamelotModule;

@RegisterCamelotModule
public class StickyRolesModule extends CamelotModule.WithDatabase<StickyRoles> {
    public StickyRolesModule(ModuleProvider.Context context) {
        super(context, StickyRoles.class);
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
