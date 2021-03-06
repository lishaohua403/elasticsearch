/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Definition;
import org.elasticsearch.painless.Variables;
import org.objectweb.asm.Label;
import org.elasticsearch.painless.MethodWriter;

/**
 * Represents a do-while loop.
 */
public final class SDo extends AStatement {

    final int maxLoopCounter;
    final SBlock block;
    AExpression condition;

    public SDo(int line, int offset, String location, int maxLoopCounter, SBlock block, AExpression condition) {
        super(line, offset, location);

        this.condition = condition;
        this.block = block;
        this.maxLoopCounter = maxLoopCounter;
    }

    @Override
    void analyze(Variables variables) {
        variables.incrementScope();

        if (block == null) {
            throw new IllegalArgumentException(error("Extraneous do while loop."));
        }

        block.beginLoop = true;
        block.inLoop = true;

        block.analyze(variables);

        if (block.loopEscape && !block.anyContinue) {
            throw new IllegalArgumentException(error("Extraneous do while loop."));
        }

        condition.expected = Definition.BOOLEAN_TYPE;
        condition.analyze(variables);
        condition = condition.cast(variables);

        if (condition.constant != null) {
            final boolean continuous = (boolean)condition.constant;

            if (!continuous) {
                throw new IllegalArgumentException(error("Extraneous do while loop."));
            }

            if (!block.anyBreak) {
                methodEscape = true;
                allEscape = true;
            }
        }

        statementCount = 1;

        if (maxLoopCounter > 0) {
            loopCounterSlot = variables.getVariable(location, "#loop").slot;
        }

        variables.decrementScope();
    }

    @Override
    void write(MethodWriter writer) {
        writer.writeStatementOffset(offset);
        Label start = new Label();
        Label begin = new Label();
        Label end = new Label();

        writer.mark(start);

        block.continu = begin;
        block.brake = end;
        block.write(writer);

        writer.mark(begin);

        condition.fals = end;
        condition.write(writer);

        writer.writeLoopCounter(loopCounterSlot, Math.max(1, block.statementCount), offset);

        writer.goTo(start);
        writer.mark(end);
    }
}
