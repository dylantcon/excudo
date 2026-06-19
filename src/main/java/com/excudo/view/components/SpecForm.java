package com.excudo.view.components;

import com.excudo.core.synthesis.spec.CommandSpec;
import javafx.scene.Node;

import java.util.function.Supplier;

/**
 * A reusable form payload for editing one {@link CommandSpec}: the
 * editable controls paired with a snapshot reader.
 *
 * <p>The same payload feeds both editing surfaces, so the form-building
 * code lives in exactly one place:
 * <ul>
 *   <li>the inline expandable row ({@code SpecRowView}) embeds
 *       {@link #node()} and calls {@link #read()} on commit, and</li>
 *   <li>the modal dialog ({@code SpecFormDialog.editSpec}) drops
 *       {@code node} into a {@code Dialog} and calls {@code read} from its
 *       result-converter.</li>
 * </ul>
 *
 * <p>{@code read} produces a fresh, immutable spec from the current
 * control values. It <b>throws</b> when those values don't form a valid
 * spec -- an unparseable number ({@link NumberFormatException}) or a
 * record's own constructor validation -- so callers can surface an inline
 * error and decline to write back rather than committing a bad value.
 *
 * @param node the editable controls (typically a {@code GridPane})
 * @param read snapshots {@code node}'s controls into a new {@code CommandSpec}
 */
public record SpecForm(Node node, Supplier<CommandSpec> read) {}
