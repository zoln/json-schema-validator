/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.networknt.schema.walk.DefaultItemWalkListenerRunner;
import com.networknt.schema.walk.WalkListenerRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ItemsValidator202012 extends BaseJsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(ItemsValidator202012.class);

    private final JsonSchema schema;
    private final WalkListenerRunner arrayItemWalkListenerRunner;
    private final int prefixCount;

    public ItemsValidator202012(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.ITEMS_202012, validationContext);

        JsonNode prefixItems = parentSchema.getSchemaNode().get("prefixItems");
        if (prefixItems instanceof ArrayNode) {
            this.prefixCount = ((ArrayNode) prefixItems).size();
        } else if (null == prefixItems) {
            this.prefixCount = 0;
        } else {
            throw new IllegalArgumentException("The value of 'prefixItems' must be an array of JSON Schema.");
        }

        if (schemaNode.isObject() || schemaNode.isBoolean()) {
            this.schema = new JsonSchema(validationContext, schemaPath, parentSchema.getCurrentUri(), schemaNode, parentSchema);
        } else {
            throw new IllegalArgumentException("The value of 'items' MUST be a valid JSON Schema.");
        }

        arrayItemWalkListenerRunner = new DefaultItemWalkListenerRunner(validationContext.getConfig().getArrayItemWalkListeners());

        this.validationContext = validationContext;

        parseErrorCode(getValidatorType().getErrorCodeKey());
    }

    public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        Set<ValidationMessage> errors = new LinkedHashSet<ValidationMessage>();

        // ignores non-arrays
        if (node.isArray()) {
            for (int i = this.prefixCount; i < node.size(); ++i) {
                // validate with item schema (the whole array has the same item schema)
                errors.addAll(schema.validate(node.get(i), rootNode, atPath(at, i)));
            }
        }

        return Collections.unmodifiableSet(errors);
    }

    @Override
    public Set<ValidationMessage> walk(JsonNode node, JsonNode rootNode, String at, boolean shouldValidateSchema) {
        Set<ValidationMessage> validationMessages = new LinkedHashSet<ValidationMessage>();

        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            JsonNode defaultNode = null;
            if (applyDefaultsStrategy.shouldApplyArrayDefaults() && schema != null) {
                defaultNode = schema.getSchemaNode().get("default");
            }
            for (int i = this.prefixCount; i < node.size(); ++i) {
                JsonNode n = node.get(i);
                if (n.isNull() && defaultNode != null) {
                    arrayNode.set(i, defaultNode);
                    n = defaultNode;
                }
                // Walk the schema.
                walkSchema(schema, n, rootNode, atPath(at, i), shouldValidateSchema, validationMessages);
            }
        }

        return validationMessages;
    }

    private void walkSchema(JsonSchema walkSchema, JsonNode node, JsonNode rootNode, String at, boolean shouldValidateSchema, Set<ValidationMessage> validationMessages) {
        //@formatter:off
        boolean executeWalk = arrayItemWalkListenerRunner.runPreWalkListeners(
            ValidatorTypeCode.ITEMS.getValue(),
            node,
            rootNode,
            at,
            walkSchema.getSchemaPath(),
            walkSchema.getSchemaNode(),
            walkSchema.getParentSchema(),
            validationContext,
            validationContext.getJsonSchemaFactory()
        );
        if (executeWalk) {
            validationMessages.addAll(walkSchema.walk(node, rootNode, at, shouldValidateSchema));
        }
        arrayItemWalkListenerRunner.runPostWalkListeners(
            ValidatorTypeCode.ITEMS.getValue(),
            node,
            rootNode,
            at,
            walkSchema.getSchemaPath(),
            walkSchema.getSchemaNode(),
            walkSchema.getParentSchema(),
            validationContext,
            validationContext.getJsonSchemaFactory(),
            validationMessages
        );
        //@formatter:on
    }

    public JsonSchema getSchema() {
        return schema;
    }

    @Override
    public void preloadJsonSchema() {
        schema.initializeValidators();
    }

}