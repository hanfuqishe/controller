/*
* Generated file
*
* Generated from: yang module name: opendaylight-sal-binding-broker-impl yang module local name: binding-forwarded-data-broker
* Generated by: org.opendaylight.controller.config.yangjmxgenerator.plugin.JMXGenerator
* Generated at: Fri May 16 17:18:18 CEST 2014
*
* Do not modify this file unless it is present under src/main directory
*/
package org.opendaylight.controller.config.yang.md.sal.binding.impl;

import org.opendaylight.controller.config.api.DependencyResolver;
import org.osgi.framework.BundleContext;

public class BindingAsyncDataBrokerImplModuleFactory extends org.opendaylight.controller.config.yang.md.sal.binding.impl.AbstractBindingAsyncDataBrokerImplModuleFactory {




    @Override
    public BindingAsyncDataBrokerImplModule instantiateModule(final String instanceName,
            final DependencyResolver dependencyResolver, final BindingAsyncDataBrokerImplModule oldModule,
            final AutoCloseable oldInstance, final BundleContext bundleContext) {
        BindingAsyncDataBrokerImplModule module = super.instantiateModule(instanceName, dependencyResolver, oldModule, oldInstance, bundleContext);
        module.setBundleContext(bundleContext);
        return module;
    }

    @Override
    public BindingAsyncDataBrokerImplModule instantiateModule(final String instanceName,
            final DependencyResolver dependencyResolver, final BundleContext bundleContext) {
        // TODO Auto-generated method stub
        BindingAsyncDataBrokerImplModule module = super.instantiateModule(instanceName, dependencyResolver, bundleContext);
        module.setBundleContext(bundleContext);
        return module;
    }
}
