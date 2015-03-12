/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron.ProviderLoaderServiceUtil.providerLoaderServiceName;

import java.security.Provider;
import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * A {@link ResourceDefinition} for a loader of {@link Provider}s.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ProviderLoaderDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition MODULE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MODULE, ModelType.STRING, true)
        .setAllowExpression(true)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
        .build();

    static final SimpleAttributeDefinition SLOT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SLOT, ModelType.STRING, true)
        .setAllowExpression(true)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final StringListAttributeDefinition CLASSES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.CLASSES)
        .setAllowExpression(true)
        .setAllowNull(true)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    // TODO - Classes is not really going to work, instead need a set of sub-definitions so that config can be supplied.

    static final SimpleAttributeDefinition REGISTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REGISTER, ModelType.BOOLEAN, true)
        .setDefaultValue(new ModelNode(false))
        .setAllowExpression(true)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] { MODULE, SLOT, CLASSES, REGISTER };

    private static final AbstractAddStepHandler ADD = new ProviderAddHandler();
    private static final OperationStepHandler REMOVE = new ProviderRemoveHandler(ADD);
    private static final OperationStepHandler WRITE = new WriteAttributeHandler();

    public ProviderLoaderDefinition() {
        super(PathElement.pathElement(ElytronDescriptionConstants.PROVIDER_LOADER),
                ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.PROVIDER_LOADER),
                ADD, REMOVE,
                OperationEntry.Flag.RESTART_RESOURCE_SERVICES,
                OperationEntry.Flag.RESTART_RESOURCE_SERVICES);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : CONFIG_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
    }

    private static class WriteAttributeHandler extends RestartParentWriteAttributeHandler {

        WriteAttributeHandler() {
            super(ElytronDescriptionConstants.PROVIDER_LOADER, CONFIG_ATTRIBUTES);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress arg0) {
            return null;
        }

    }

    private static class ProviderAddHandler extends AbstractAddStepHandler {

        ProviderAddHandler() {
            super(CONFIG_ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ModelNode model = resource.getModel();
            String module = asStringIfDefined(context, ProviderLoaderDefinition.MODULE, model);
            String slot = asStringIfDefined(context, ProviderLoaderDefinition.SLOT, model);
            String[] classNames = asStringArrayIfDefined(context, ProviderLoaderDefinition.CLASSES, model);
            boolean register = ProviderLoaderDefinition.REGISTER.resolveModelAttribute(context, model).asBoolean();

            Service<Provider[]> providerLoaderService = ProviderLoaderService.newInstance(module, slot, classNames, register);
            ServiceName serviceName = providerLoaderServiceName(operation);
            ServiceTarget serviceTarget = context.getServiceTarget();
            ServiceBuilder<Provider[]> serviceBuilder = serviceTarget.addService(serviceName, providerLoaderService)
                    .setInitialMode(Mode.ACTIVE);

            serviceBuilder.install();
        }

    }

    private static String[] asStringArrayIfDefined(OperationContext context, StringListAttributeDefinition attributeDefintion, ModelNode model) throws OperationFailedException {
        ModelNode resolved = attributeDefintion.resolveModelAttribute(context, model);
        if (resolved.isDefined()) {
            List<ModelNode> values = resolved.asList();
            String[] response = new String[values.size()];
            for (int i = 0; i < response.length; i++) {
                response[i] = values.get(i).asString();
            }
            return response;
        }
        return null;
    }

    private static class ProviderRemoveHandler extends ServiceRemoveStepHandler {

        protected ProviderRemoveHandler(AbstractAddStepHandler addOperation) {
            super(addOperation);
        }

    }

}