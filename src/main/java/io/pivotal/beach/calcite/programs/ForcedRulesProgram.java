package io.pivotal.beach.calcite.programs;

import org.apache.calcite.plan.*;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.Program;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelBuilderFactory;
import org.apache.calcite.util.Litmus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ForcedRulesProgram implements Program {
	private static final Logger logger = LoggerFactory.getLogger(ForcedRulesProgram.class);

	private final BasicForcedRule[] rules;

	public ForcedRulesProgram(BasicForcedRule... rules) {
		this.rules = rules;
	}

	@Override
	public RelNode run(
			RelOptPlanner planner,
			RelNode rel,
			RelTraitSet requiredOutputTraits,
			List<RelOptMaterialization> materializations,
			List<RelOptLattice> lattices
	) {
		logger.debug("Running forced rules on:\n" + RelOptUtil.toString(rel));
		return replace(rel, rules, RelBuilder.proto(planner.getContext()));
	}

	private RelNode replace(RelNode original, BasicForcedRule[] rules, RelBuilderFactory relBuilderFactory) {
		RelNode p = original;
		for (BasicForcedRule rule : rules) {
			RelNode updated = rule.apply(p, relBuilderFactory);
			if (updated != null) {
				logger.trace("Rule: " + rule.toString() +
						"\nReplacing:\n" + RelOptUtil.toString(p) +
						"\nWith:\n" + RelOptUtil.toString(updated)
				);
				// Must maintain row types so that nothing explodes
				RelOptUtil.equal(
						"rowtype of original", p.getRowType(),
						"rowtype of replaced", updated.getRowType(),
						Litmus.THROW
				);
				p = updated;
			}
		}

		if (p == original) { // optimisation: avoid changing nodes inside stuff we changed
			List<RelNode> oldInputs = p.getInputs();
			for (int i = 0; i < oldInputs.size(); i++) {
				RelNode originalInput = oldInputs.get(i);
				RelNode replacedInput = replace(originalInput, rules, relBuilderFactory);
				if (replacedInput != originalInput) {
					p.replaceInput(i, replacedInput);
				}
			}
		}
		return p;
	}
}