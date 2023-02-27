package com.apollographql.apollo3.compiler.ir

import com.apollographql.apollo3.ast.GQLEnumTypeDefinition
import com.apollographql.apollo3.ast.GQLInputObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLInterfaceTypeDefinition
import com.apollographql.apollo3.ast.GQLObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLScalarTypeDefinition
import com.apollographql.apollo3.ast.GQLUnionTypeDefinition
import com.apollographql.apollo3.ast.Schema

object IrSchemaBuilder {
  fun build(
      schema: Schema,
      irOperations: IrOperations,
      incomingTypes: Set<String>,
  ): IrSchema {
    check(irOperations is DefaultIrOperations)

    val irEnums = mutableListOf<IrEnum>()
    val irScalars = mutableListOf<IrScalar>()
    val irInputObjects = mutableListOf<IrInputObject>()
    val irUnions = mutableListOf<IrUnion>()
    val irInterfaces = mutableListOf<IrInterface>()
    val irObjects = mutableListOf<IrObject>()

    val usedFields = irOperations.usedFields
    val usedTypes = irOperations.usedTypes

    val visitedTypes = incomingTypes.toMutableSet()
    val typesStack = usedTypes.toMutableList()
    while (typesStack.isNotEmpty()) {
      val name = typesStack.removeFirst()
      if (visitedTypes.contains(name)) {
        continue
      }

      visitedTypes.add(name)
      val typeDefinition = schema.typeDefinition(name)

      when {
        typeDefinition.isBuiltIn() && typeDefinition !is GQLScalarTypeDefinition && typeDefinition !is GQLEnumTypeDefinition -> {
          // do nothing, introspection types are in apollo-api
        }
        typeDefinition is GQLScalarTypeDefinition -> {
          irScalars.add(typeDefinition.toIr(schema))
        }
        typeDefinition is GQLEnumTypeDefinition -> {
          irEnums.add(typeDefinition.toIr(schema))
        }
        typeDefinition is GQLInputObjectTypeDefinition -> {
          irInputObjects.add(typeDefinition.toIr(schema))
        }
        typeDefinition is GQLUnionTypeDefinition -> {
          irUnions.add(typeDefinition.toIr(schema))
        }
        typeDefinition is GQLInterfaceTypeDefinition -> {
          irInterfaces.add(typeDefinition.toIr(schema, usedFields))
        }
        typeDefinition is GQLObjectTypeDefinition -> {
          irObjects.add(typeDefinition.toIr(schema, usedFields))
        }
      }
    }

    return DefaultIrSchema(
        irScalars = irScalars,
        irEnums = irEnums,
        irInputObjects = irInputObjects,
        irUnions = irUnions,
        irInterfaces = irInterfaces,
        irObjects = irObjects,
        connectionTypes = schema.connectionTypes.toList()
    )
  }
}
