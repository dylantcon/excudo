package com.excudo.core.commands;

import java.util.List;

/**
 * A command that adds shapes to a slide. execute_commands reports the new ids,
 * so the agent can style or animate what it just added without listing the
 * slide's shapes first.
 */
public interface CreatesShapes {

    /** Ids of the shapes this command added; empty before it has run. */
    List<Integer> getCreatedSpids();
}
