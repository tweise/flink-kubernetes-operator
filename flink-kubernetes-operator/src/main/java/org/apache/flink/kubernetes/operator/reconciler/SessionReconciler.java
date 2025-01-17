/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.kubernetes.operator.reconciler;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.kubernetes.operator.crd.FlinkDeployment;
import org.apache.flink.kubernetes.operator.crd.status.FlinkDeploymentStatus;
import org.apache.flink.kubernetes.operator.service.FlinkService;
import org.apache.flink.kubernetes.operator.utils.IngressUtils;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciler responsible for handling the session cluster lifecycle according to the desired and
 * current states.
 */
public class SessionReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(SessionReconciler.class);

    private final KubernetesClient kubernetesClient;
    private final FlinkService flinkService;

    public SessionReconciler(KubernetesClient kubernetesClient, FlinkService flinkService) {
        this.kubernetesClient = kubernetesClient;
        this.flinkService = flinkService;
    }

    public boolean reconcile(
            String operatorNamespace, FlinkDeployment flinkApp, Configuration effectiveConfig)
            throws Exception {
        if (flinkApp.getStatus() == null) {
            flinkApp.setStatus(new FlinkDeploymentStatus());
            try {
                flinkService.submitSessionCluster(flinkApp, effectiveConfig);
                IngressUtils.updateIngressRules(
                        flinkApp, effectiveConfig, operatorNamespace, kubernetesClient, false);
                return true;
            } catch (Exception e) {
                LOG.error("Error while deploying " + flinkApp.getMetadata().getName(), e);
                return false;
            }
        }

        boolean specChanged = !flinkApp.getSpec().equals(flinkApp.getStatus().getSpec());

        if (specChanged) {
            if (flinkApp.getStatus().getSpec().getJob() != null) {
                throw new RuntimeException("Cannot switch from job to session cluster");
            }
            upgradeSessionCluster(flinkApp, effectiveConfig);
        }
        return true;
    }

    private void upgradeSessionCluster(FlinkDeployment flinkApp, Configuration effectiveConfig)
            throws Exception {
        flinkService.stopSessionCluster(flinkApp, effectiveConfig);
        flinkService.submitSessionCluster(flinkApp, effectiveConfig);
    }
}
