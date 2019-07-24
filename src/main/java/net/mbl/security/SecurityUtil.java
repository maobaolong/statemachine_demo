/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package net.mbl.security;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Security Utils.
 */
public final class SecurityUtil {
    public static final Logger LOG = LoggerFactory.getLogger(SecurityUtil.class);
    @VisibleForTesting
    static HostResolver hostResolver = new StandardHostResolver();

    private SecurityUtil() {
    }

    /**
     * TGS must have the server principal of the form "krbtgt/FOO@FOO".
     *
     * @param principal
     * @return true or false
     */
    static boolean
    isTGSPrincipal(KerberosPrincipal principal) {
        if (principal == null) {
            return false;
        }
        if (principal.getName().equals("krbtgt/" + principal.getRealm() +
                "@" + principal.getRealm())) {
            return true;
        }
        return false;
    }

    /**
     * Check whether the server principal is the TGS's principal
     *
     * @param ticket the original TGT (the ticket that is obtained when a
     *               kinit is done)
     * @return true or false
     */
    protected static boolean isOriginalTGT(KerberosTicket ticket) {
        return isTGSPrincipal(ticket.getServer());
    }

    private static String[] getComponents(String principalConfig) {
        if (principalConfig == null) {
            return null;
        }
        return principalConfig.split("[/@]");
    }

    /**
     * Resolves a host subject to the security requirements determined by
     * hadoop.security.token.service.use_ip. Optionally logs slow resolutions.
     *
     * @param hostname host or ip to resolve
     * @return a resolved host
     * @throws UnknownHostException if the host doesn't exist
     */
    public static InetAddress getByName(String hostname) throws UnknownHostException {
        return hostResolver.getByName(hostname);
    }

    interface HostResolver {
        InetAddress getByName(String host) throws UnknownHostException;
    }

    /**
     * Uses standard java host resolution
     */
    static class StandardHostResolver implements HostResolver {
        @Override
        public InetAddress getByName(String host) throws UnknownHostException {
            return InetAddress.getByName(host);
        }
    }
}
