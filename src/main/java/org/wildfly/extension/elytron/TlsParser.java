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

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEYSTORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PASSWORD;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PATH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_LOADER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.RELATIVE_TO;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REQUIRED;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TYPE;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A parser for the TLS related definitions.
 *
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class TlsParser {

    /*
     * KeyStores
     */

    void readKeyStores(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (KEYSTORE.equals(localName)) {
                readKeyStore(parentAddress, reader, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readKeyStore(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addKeyStore = new ModelNode();
        addKeyStore.get(OP).set(ADD);
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, TYPE }));
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case TYPE:
                        KeyStoreDefinition.TYPE.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case PROVIDER:
                        KeyStoreDefinition.PROVIDER.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case PROVIDER_LOADER:
                        KeyStoreDefinition.PROVIDER_LOADER.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    case PASSWORD:
                        KeyStoreDefinition.PASSWORD.parseAndSetParameter(value, addKeyStore, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addKeyStore.get(OP_ADDR).set(parentAddress).add(KEYSTORE, name);
        list.add(addKeyStore);

        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (FILE.equals(localName)) {
                readFile(addKeyStore, reader, list);
            } else {
                throw unexpectedElement(reader);
            }
        }

    }

    private void readFile(ModelNode addOp, XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        boolean pathFound = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case RELATIVE_TO:
                        FileAttributeDefinitions.RELATIVE_TO.parseAndSetParameter(value, addOp, reader);
                        break;
                    case PATH:
                        pathFound = true;
                        FileAttributeDefinitions.PATH.parseAndSetParameter(value, addOp, reader);
                        break;
                    case REQUIRED:
                        KeyStoreDefinition.REQUIRED.parseAndSetParameter(value, addOp, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (pathFound == false) {
            throw missingRequired(reader, PATH);
        }
        requireNoContent(reader);
    }

    void writeKeyStore(String name, ModelNode keyStore, XMLExtendedStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(KEYSTORE);
        writer.writeAttribute(NAME, name);
        KeyStoreDefinition.TYPE.marshallAsAttribute(keyStore, writer);
        KeyStoreDefinition.PROVIDER.marshallAsAttribute(keyStore, writer);
        KeyStoreDefinition.PROVIDER_LOADER.marshallAsAttribute(keyStore, writer);
        KeyStoreDefinition.PASSWORD.marshallAsAttribute(keyStore, writer);

        if (keyStore.hasDefined(PATH)) {
            writer.writeStartElement(FILE);
            FileAttributeDefinitions.RELATIVE_TO.marshallAsAttribute(keyStore, writer);
            FileAttributeDefinitions.PATH.marshallAsAttribute(keyStore, writer);
            KeyStoreDefinition.REQUIRED.marshallAsAttribute(keyStore, writer);

            writer.writeEndElement();
        }
        writer.writeEndElement();
    }
}
