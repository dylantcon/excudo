package com.excudo.core.synthesis;

import com.excudo.core.synthesis.spec.CommandSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable DAG container for a synthesized script. Nodes are
 * {@link CommandSpec} values; edges are "must-execute-before"
 * dependencies. The DAG -- rather than a linear list -- preserves the
 * fact that many spec pairs have no ordering requirement (two
 * independent shape additions can run in either order) and lets the
 * synthesizer avoid inventing one.
 *
 * <p>All query operations are pure -- no mutation, no IO. Build via
 * {@link Builder}; consume via {@link #topologicalOrder()},
 * {@link #subScript(int)}, etc.
 */
public final class CommandScript {

    /** Insertion-ordered to keep deterministic iteration. */
    private final Set<CommandSpec> nodes;

    /** Adjacency: {@code dependencies.get(X)} is the set of specs that
     *  must finish executing <em>before</em> X starts. */
    private final Map<CommandSpec, Set<CommandSpec>> dependencies;

    private CommandScript(Set<CommandSpec> nodes, Map<CommandSpec, Set<CommandSpec>> deps) {
        this.nodes = Collections.unmodifiableSet(new LinkedHashSet<>(nodes));
        Map<CommandSpec, Set<CommandSpec>> frozen = new HashMap<>();
        for (var e : deps.entrySet()) {
            frozen.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        this.dependencies = Collections.unmodifiableMap(frozen);
    }

    /** Empty script. */
    public static CommandScript empty() {
        return new CommandScript(Collections.emptySet(), Collections.emptyMap());
    }

    public Set<CommandSpec> nodes() { return nodes; }

    /** The set of specs that must finish before {@code spec} starts,
     *  or an empty set if {@code spec} has no dependencies or isn't
     *  present in the script. */
    public Set<CommandSpec> dependenciesOf(CommandSpec spec) {
        return dependencies.getOrDefault(spec, Collections.emptySet());
    }

    public int size() { return nodes.size(); }
    public boolean isEmpty() { return nodes.isEmpty(); }

    // ========== Topological order ==========

    /**
     * Return a linear execution order satisfying every edge. If two
     * specs have no dependency relationship their relative order is
     * the DAG's insertion order -- stable across calls. Throws if the
     * graph contains a cycle (which the builder already prevents, but
     * the topo-sort double-checks for safety).
     */
    public List<CommandSpec> topologicalOrder() {
        Map<CommandSpec, Integer> indegree = new HashMap<>();
        for (CommandSpec n : nodes) indegree.put(n, 0);
        for (CommandSpec n : nodes) {
            for (CommandSpec dep : dependenciesOf(n)) {
                // dep -> n edge: n's indegree counts how many deps it has
                indegree.merge(n, 0, Integer::sum); // ensure key present
            }
        }
        // Rebuild indegrees properly: indegree[n] = |deps of n|.
        for (CommandSpec n : nodes) {
            indegree.put(n, dependenciesOf(n).size());
        }

        // Kahn's algorithm. Use a deque as FIFO so ready-specs drain
        // in insertion order (preserves DAG's native iteration order).
        Deque<CommandSpec> ready = new ArrayDeque<>();
        for (CommandSpec n : nodes) {
            if (indegree.get(n) == 0) ready.addLast(n);
        }

        // Inverted adjacency: for a node N, which specs depend on N?
        Map<CommandSpec, Set<CommandSpec>> dependents = new HashMap<>();
        for (CommandSpec n : nodes) dependents.put(n, new LinkedHashSet<>());
        for (CommandSpec n : nodes) {
            for (CommandSpec dep : dependenciesOf(n)) {
                dependents.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(n);
            }
        }

        List<CommandSpec> result = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            CommandSpec n = ready.pollFirst();
            result.add(n);
            for (CommandSpec dependent : dependents.getOrDefault(n, Collections.emptySet())) {
                int d = indegree.merge(dependent, -1, Integer::sum);
                if (d == 0) ready.addLast(dependent);
            }
        }

        if (result.size() != nodes.size()) {
            throw new IllegalStateException("CommandScript contains a cycle; cannot topologically sort");
        }
        return result;
    }

    // ========== Sub-scripts ==========

    /** Extract the specs that target a specific slide. Edges between
     *  surviving nodes are preserved; edges to pruned nodes are dropped. */
    public CommandScript subScript(int slideNumber) {
        return subScript(s -> s.slideNumber() == slideNumber);
    }

    public CommandScript subScript(Predicate<CommandSpec> filter) {
        Set<CommandSpec> survivors = new LinkedHashSet<>();
        for (CommandSpec n : nodes) if (filter.test(n)) survivors.add(n);

        Map<CommandSpec, Set<CommandSpec>> prunedDeps = new HashMap<>();
        for (CommandSpec n : survivors) {
            Set<CommandSpec> origDeps = dependenciesOf(n);
            Set<CommandSpec> remaining = new LinkedHashSet<>();
            for (CommandSpec d : origDeps) if (survivors.contains(d)) remaining.add(d);
            if (!remaining.isEmpty()) prunedDeps.put(n, remaining);
        }
        return new CommandScript(survivors, prunedDeps);
    }

    // ========== Diff ==========

    /** Nodes present in {@code this} but not in {@code other}. */
    public Set<CommandSpec> minus(CommandScript other) {
        Set<CommandSpec> result = new LinkedHashSet<>(nodes);
        result.removeAll(other.nodes);
        return Collections.unmodifiableSet(result);
    }

    // ========== Equality / hashing ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandScript that)) return false;
        return nodes.equals(that.nodes) && dependencies.equals(that.dependencies);
    }

    @Override
    public int hashCode() {
        // Node set hashCode is order-independent already (Set contract),
        // matching the semantic that two scripts with the same nodes +
        // same dependency graph are equal regardless of the order they
        // were built in.
        return Objects.hash(nodes, dependencies);
    }

    // ========== Builder ==========

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Set<CommandSpec> nodes = new LinkedHashSet<>();
        private final Map<CommandSpec, Set<CommandSpec>> deps = new HashMap<>();

        public Builder add(CommandSpec spec) {
            Objects.requireNonNull(spec, "spec");
            nodes.add(spec);
            return this;
        }

        public Builder addAll(Iterable<? extends CommandSpec> specs) {
            for (CommandSpec s : specs) add(s);
            return this;
        }

        /** Declare that {@code spec} depends on {@code prerequisite} --
         *  the prereq must finish executing before {@code spec} runs.
         *  Both must have been added via {@link #add} first. */
        public Builder dependsOn(CommandSpec spec, CommandSpec prerequisite) {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(prerequisite, "prerequisite");
            if (!nodes.contains(spec)) {
                throw new IllegalArgumentException("spec not added: " + spec);
            }
            if (!nodes.contains(prerequisite)) {
                throw new IllegalArgumentException("prerequisite not added: " + prerequisite);
            }
            if (spec.equals(prerequisite)) {
                throw new IllegalArgumentException("a spec cannot depend on itself");
            }
            deps.computeIfAbsent(spec, k -> new LinkedHashSet<>()).add(prerequisite);
            return this;
        }

        /** Build the script. Detects cycles via a topo-sort dry run so
         *  downstream consumers never receive a structurally-invalid DAG. */
        public CommandScript build() {
            CommandScript s = new CommandScript(nodes, deps);
            // Eagerly run topo-sort to surface cycles at build time
            // rather than at execute time. The check is O(V+E) and the
            // DAGs here are small.
            s.topologicalOrder();
            return s;
        }
    }

    // ========== Convenience: build from a flat list ==========

    /** Build a script from a flat spec list with no declared dependencies.
     *  Every spec is an independent top-level node; execution order is
     *  whatever the topo-sort picks (insertion order, stable). */
    public static CommandScript ofFlat(List<CommandSpec> specs) {
        Builder b = builder();
        for (CommandSpec s : specs) b.add(s);
        return b.build();
    }

    /** Shorthand for the set of slide numbers this script touches. */
    public Set<Integer> slideNumbers() {
        Set<Integer> out = new LinkedHashSet<>();
        for (CommandSpec n : nodes) out.add(n.slideNumber());
        return Collections.unmodifiableSet(out);
    }

    /** Diagnostic: specs that have zero dependents (terminal nodes). */
    public Set<CommandSpec> terminalNodes() {
        Set<CommandSpec> allDeps = new HashSet<>();
        for (Set<CommandSpec> dl : dependencies.values()) allDeps.addAll(dl);
        Set<CommandSpec> out = new LinkedHashSet<>(nodes);
        out.removeAll(allDeps);
        return Collections.unmodifiableSet(out);
    }
}
