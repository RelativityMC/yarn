package net.fabricmc.filament.nameproposal.field.nameprovider;

import java.util.function.Predicate;

import org.objectweb.asm.tree.FieldInsnNode;

import net.fabricmc.filament.nameproposal.MappingEntry;
import net.fabricmc.filament.nameproposal.field.FieldData;

/**
 * A field name provider that returns a *proposed* name from the first field
 * argument of the method call that initializes the field.
 */
public record RecursiveArgumentFieldNameProvider(Predicate<String> trustedOwnerPredicate) implements FieldNameProvider {
	@Override
	public String getName(FieldData field) {
		FieldInsnNode arg = getFirstFieldArg(field);
		if (arg == null) return null;

		if (!this.trustedOwnerPredicate().test(arg.owner)) return null;

		return field.proposedFieldNames().get(new MappingEntry(arg.owner, arg.name, arg.desc));
	}

	private static FieldInsnNode getFirstFieldArg(FieldData field) {
		for (Object arg : field.args()) {
			if (arg instanceof FieldInsnNode node) {
				return node;
			}
		}

		return null;
	}
}
