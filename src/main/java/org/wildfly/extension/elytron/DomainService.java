/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.auth.login.SecurityDomain;
import org.wildfly.security.auth.login.SecurityDomain.RealmBuilder;
import org.wildfly.security.auth.spi.SecurityRealm;
import org.wildfly.security.auth.util.NameRewriter;
import org.wildfly.security.auth.util.RealmMapper;
import org.wildfly.security.authz.RoleDecoder;


/**
 * A {@link Service} responsible for managing the lifecycle of a single {@link SecurityDomain}.
 *
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DomainService implements Service<SecurityDomain> {

    private volatile SecurityDomain securityDomain;

    private final String name;
    private final String defaultRealm;
    private String preRealmNameRewriter;
    private String postRealmNameRewriter;

    private final Map<String, RealmDependency> realms = new HashMap<>();
    private final Map<String, InjectedValue<NameRewriter>> nameRewriters = new HashMap<>();

    private final InjectedValue<RealmMapper> realmMapperInjector = new InjectedValue<RealmMapper>();

    DomainService(final String name, final String defaultRealm) {
        this.name = name;
        this.defaultRealm = defaultRealm;
    }

    RealmDependency createRealmDependency(final String realmName) throws OperationFailedException {
        if (realms.containsKey(realmName)) {
            throw ROOT_LOGGER.duplicateRealmInjection(name, realmName);
        }

        RealmDependency realmDependency = new RealmDependency();
        realms.put(realmName, realmDependency);
        return realmDependency;
    }

    private Injector<NameRewriter> createNameRewriterInjector(final String nameRewriterName) {
        if (nameRewriters.containsKey(nameRewriterName)) {
            return null; // i.e. should already be injected for this name.
        }

        InjectedValue<NameRewriter> nameRewriterInjector = new InjectedValue<>();
        nameRewriters.put(nameRewriterName, nameRewriterInjector);
        return nameRewriterInjector;
    }

    Injector<RealmMapper> getRealmMapperInjector() {
        return realmMapperInjector;
    }

    Injector<NameRewriter> createPreRealmNameRewriterInjector(final String name) {
        this.preRealmNameRewriter = name;

        return createNameRewriterInjector(name);
    }

    Injector<NameRewriter> createPostRealmNameRewriterInjector(final String name) {
        this.postRealmNameRewriter = name;

        return createNameRewriterInjector(name);
    }

    @Override
    public void start(StartContext context) throws StartException {
        SecurityDomain.Builder builder = SecurityDomain.builder();

        if (preRealmNameRewriter != null) {
            builder.setPreRealmRewriter(nameRewriters.get(preRealmNameRewriter).getValue());
        }
        if (postRealmNameRewriter != null) {
            builder.setPostRealmRewriter(nameRewriters.get(postRealmNameRewriter).getValue());
        }

        RealmMapper realmMapper = realmMapperInjector.getOptionalValue();
        if (realmMapper != null) {
            builder.setRealmMapper(realmMapper);
        }

        builder.setDefaultRealmName(defaultRealm);
        for (Entry<String, RealmDependency> entry : realms.entrySet()) {
            String realmName = entry.getKey();
            RealmDependency realmDependency = entry.getValue();
            RealmBuilder realmBuilder = builder.addRealm(realmName, realmDependency.securityRealmInjector.getValue());
            if (realmDependency.nameRewriter != null) {
                realmBuilder.setNameRewriter(nameRewriters.get(realmDependency.nameRewriter).getValue());
            }
            RoleDecoder roleDecoder = realmDependency.roleDecoderInjector.getOptionalValue();
            if (roleDecoder != null) {
                realmBuilder.setRoleDecoder(roleDecoder);
            }
        }

        securityDomain = builder.build();
    }

    @Override
    public void stop(StopContext context) {
       securityDomain = null;
    }

    @Override
    public SecurityDomain getValue() throws IllegalStateException, IllegalArgumentException {
        return securityDomain;
    }

    class RealmDependency {

        private InjectedValue<SecurityRealm> securityRealmInjector = new InjectedValue<>();

        private String nameRewriter;

        private InjectedValue<RoleDecoder> roleDecoderInjector = new InjectedValue<>();

        Injector<SecurityRealm> getSecurityRealmInjector() {
            return securityRealmInjector;
        }

        Injector<NameRewriter> getNameRewriterInjector(final String name) {
            this.nameRewriter = name;
            return createNameRewriterInjector(name);
        }

        Injector<RoleDecoder> getRoleDecoderInjector() {
            return roleDecoderInjector;
        }

    }
}
