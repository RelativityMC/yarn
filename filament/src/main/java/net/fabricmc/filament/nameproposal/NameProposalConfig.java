/*
 * Copyright (c) 2023 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.filament.nameproposal;

import java.util.List;
import java.util.Set;

import net.fabricmc.filament.nameproposal.field.nameprovider.ConditionalFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.ConstantFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.FieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.ModifyingFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.RecursiveArgumentFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.SequenceFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.nameprovider.StringArgumentFieldNameProvider;
import net.fabricmc.filament.nameproposal.field.predicate.DescriptorFieldPredicate;
import net.fabricmc.filament.nameproposal.field.predicate.InternalInitFieldPredicate;
import net.fabricmc.filament.nameproposal.field.predicate.MethodOwnerFieldPredicate;
import net.fabricmc.filament.nameproposal.field.predicate.StaticFieldPredicate;

public record NameProposalConfig(FieldNameProvider fieldNameProvider) {
	// trusted owners, currently for most IDs
	private static final Set<String> TRUSTED_ID_OWNERS = Set.of(
			"net/minecraft/class_3612",
			"net/minecraft/class_9796",
			"net/minecraft/class_1_779",
			"net/minecraft/class_1_780",
			"net/minecraft/class_1_781",
			"net/minecraft/class_1_786",
			"net/minecraft/class_1_789",
			"net/minecraft/class_1_793",
			"net/minecraft/class_1_819"
	);

	public static final NameProposalConfig DEFAULT = new NameProposalConfig(new SequenceFieldNameProvider(List.of(
			new ConditionalFieldNameProvider(
					StringArgumentFieldNameProvider.INSTANCE,
					List.of(
							new StaticFieldPredicate(true),
							InternalInitFieldPredicate.INSTANCE
					)
			),
			new ModifyingFieldNameProvider(new ConditionalFieldNameProvider(
					new RecursiveArgumentFieldNameProvider(TRUSTED_ID_OWNERS::contains),
					List.of(
							new StaticFieldPredicate(true),
						InternalInitFieldPredicate.INSTANCE
					)
			), (name, field) -> switch (field.methodName()) {
			case "method_1_4735" -> name + "_SPAWN_EGG";
			case "method_1_4732" -> "MUSIC_DISC" + name;
			default -> name;
			}),
			// Results of BlockItemTagKey#{block, item}
			new ConditionalFieldNameProvider(
					new RecursiveArgumentFieldNameProvider("net/minecraft/class_1_780"::equals),
					List.of(
							new StaticFieldPredicate(false),
							new MethodOwnerFieldPredicate("net/minecraft/class_1_782")
					)
			),
			new ConditionalFieldNameProvider(
					new ConstantFieldNameProvider("CODEC"),
					List.of(
							new StaticFieldPredicate(true),
							new DescriptorFieldPredicate("Lcom/mojang/serialization/Codec;")
					)
			),
			new ConditionalFieldNameProvider(
					new ConstantFieldNameProvider("CODEC"),
					List.of(
							new StaticFieldPredicate(true),
							new DescriptorFieldPredicate("Lcom/mojang/serialization/MapCodec;")
					)
			)
	)));
}
