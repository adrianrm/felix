
# Felix Health Checks

Based on simple `HealthCheck` OSGi services, the Felix Health Check Tools ("hc" in short form) are used to check the health of live Felix systems, based on inputs like  OSGi framework status, JMX MBean attribute values, or any context information retrieved via any API.

Health checks are easily extensible either by configuring the supplied default `HealthCheck` services, or 
by implementing your own `HealthCheck` services to cater for project specific requirements.

However for simple setups, the out of the box health checks (bundle general checks) are often sufficient. [Executing Health Checks](#executing-health-checks)
is a good starting point to run existing checks and to get familiar with how health checks work.

See also:

* [Source code for the HealthCheck modules](http://svn.apache.org/repos/asf/felix/trunk/healthcheck)
* adaptTo() slides about Health Checks (from the time when they were part of Apache Sling):
    * [adaptTo() 2013 - Automated self-testing and health check of live Sling instances](https://adapt.to/2013/en/schedule/18_healthcheck.html)
    * [adaptTo() 2014 - New features of the sling health check](https://adapt.to/2014/en/schedule/new-features-of-the-sling-health-check.html)

## Use cases
Generally health checks have two high level use cases:

* **Load balancers can query the health of an instance** and decide to take it outor back into the list of used backends automatically
* **Operations teams checking instances** for their internal state **manually**

The strength of Health Checks are to surface internal state for external use:

* Check that all OSGi bundles are up and running
* Verify that performance counters are in range
* Ping external systems and raise alarms if they are down
* Run smoke tests at system startup 
* Check that demo content has been removed from a production system
* Check that demo accounts are disabled

The health check subsystem uses tags to select which health checks to execute so you can for example execute just the _performance_ or _security_ health 
checks once they are configured with the corresponding tags.

The out of the box health check services also allow for using them as JMX aggregators and processors, which take JMX
attribute values as input and make the results accessible via JMX MBeans.

## Implementing `HealthCheck`s

Health checks checks can be contributed by any bundle via the provided SPI interface. It is best practice to implement a health check as part of the bundle that contains the functionality being checked.

## The `HealthCheck` SPI interface

A `HealthCheck` is just an OSGi service that returns a `Result`.

```
    public interface HealthCheck {
        
        /** Execute this health check and return a {@link Result} 
         *  This is meant to execute quickly, access to external
         *  systems, for example, should be managed asynchronously.
         */
        public Result execute();
    }
```
    
A simple health check implementation might look like follows:

```
    public class SampleHealthCheck implements HealthCheck {

        @Override
        public Result execute() {
            FormattingResultLog log = new FormattingResultLog();
            ...
            log.info("Checking my context {}", myContextObject);
            if(myContextObject.retrieveStatus() != ...expected value...) {
                log.warn("Problem with ...");
            }
            if(myContextObject.retrieveOtherStatus() != ...expected value...) {
                log.critical("Cricital Problem with ...");
            }
            return new Result(log);
        }

    }
```

The `Result` is a simple immutable class that provides a `Status` via `getStatus()` (OK, WARN, CRITICAL etc.) and one or more log-like messages that
can provide more info about what, if anything, went wrong.

### Semantic meaning of health check results
In order to make health check results aggregatable in a reasonable way, it is important that result status values are used in a consistent way across different checks. When implementing custom health checks, comply to the following table:

Status | System is functional | Meaning | Actions possible for machine clients | Actions possible for human clients  
--- | --- | --- | --- | ---  
OK | yes | Everything is ok. | <ul><li>If system is not actively used yet, a load balancer might decide to take the system to production after receiving this status for the first time.</li><li>Otherwise no action needed</li></ul> | Response logs might still provide information to a human on why the system currently is healthy. E.g. it might show 30% disk used which indicates that no action will be required for a long time  
WARN | yes | **Tendency to CRITICAL** <br>System is fully functional but actions are needed to avoid a CRITICAL status in the future | <ul><li>Certain actions can be configured for known, actionable warnings, e.g. if disk space is low, it could be dynamically extended using infrastructure APIs if on virtual infrastructure)</li><li>Pass on information to monitoring system to be available to humans (in other aggregator UIs)</li></ul> | Any manual steps that a human can perform based on their knowledge to avoid the system to get to CRITICAL state
TEMPORARILY_UNAVAILABLE *) | no | **Tendency to OK** <br>System is not functional at the moment but is expected to become OK (or at least WARN) without action. An health check using this status is expected to turn CRITICAL after a certain period returning TEMPORARILY_UNAVAILABLE | <ul><li>Take out system from load balancing</li><li>Wait until TEMPORARILY_UNAVAILABLE status turns into either OK or CRITICAL</li></ul> | Wait and monitor result logs of health check returning TEMPORARILY_UNAVAILABLE
CRITICAL | no | System is not functional and must not be used | <ul><li>Take out system from load balancing</li><li>Decommission system entirely and re-provision from scratch</li></ul>  | Any manual steps that a human can perform based on their knowledge to bring the system back to state OK
HEALTH\_CHECK\_ERROR | no | **Actual status unknown** <br>There was an error in correctly calculating one of the status values above. Like CRITICAL but with the hint that the health check probe itself might be the problem (and the system could well be in state OK) | <ul><li>Treat exactly the same as CRITICAL</li></ul>  | Fix health check implementation or configuration to ensure a correct status can be calculated

*) The health check executor automatically turns checks that coninuosly return `TEMPORARILY_UNAVAILABLE` into `CRITICAL` after a certain grace period, see [Configuring the Health Check Executor](#configuring-the-health-check-executor)

### Configuring Health Checks

`HealthCheck` services are created via OSGi configurations. Generic health check service properties are interpreted by the health check executor service. Custom health check service properties can be used by the health check implementation itself to configure its behaviour.

The following generic Health Check properties may be used for all checks:

Property    | Type     | Description  
----------- | -------- | ------------
hc.name     | String   | The name of the health check as shown in UI
hc.tags     | String[] | List of tags: Both Felix Console Plugin and Health Check servlet support selecting relevant checks by providing a list of tags
hc.mbean.name | String | Makes the HC result available via given MBean name. If not provided no MBean is created for that `HealthCheck`
hc.async.cronExpression | String | Used to schedule the execution of a `HealthCheck` at regular intervals, using a cron expression as supported by the [Quartz Cron Trigger](http://www.quartz-scheduler.org/api/previous_versions/1.8.5/org/quartz/CronTrigger.html) module. 
hc.async.intervalInSec | Long | Used to schedule the execution of a `HealthCheck` at regular intervals, specifying a period in seconds
hc.resultCacheTtlInMs | Long | Overrides the global default TTL as configured in health check executor for health check responses
hc.keepNonOkResultsStickyForSec | Long | If given, non-ok results from past executions will be taken into account as well for the given seconds (use Long.MAX_VALUE for indefinitely). Useful for unhealthy system states that disappear but might leave the system at an inconsistent state (e.g. an event queue overflow where somebody needs to intervene manually) or for checks that should only go back to OK with a delay (can be useful for load balancers).

All service properties are optional.

### Annotations to simplify configuration of custom Health Checks

To configure the defaults for the service properties [above](#configuring-health-checks), the following annotations can be used:

    @Component // SCR component (standard OSGi)

    // optional, if the check is to be made configurable (standard OSGi)
    @Designate(ocd = MyCustomCheckConfig.class, factory = true)  
    
    // set name and tags
    @HealthCheckService(name = "Custom Check Name", tags= {"tag1", "tag2"})
    
    // make the check asynchronous, either cronExpression or intervalInSec has to be provided
    @Async(cronExpression="0 0 12 1 * ?" /*, intervalInSec = 60 */)
    
    // to set `hc.async.cronExpression`:
    @ResultTTL(resultCacheTtlInMs = 10000)
    
    // to set `hc.mbean.name`:
    @HealthCheckMBean(name = "MyCustomCheck")

    // to set `hc.keepNonOkResultsStickyForSec`:
    @Sticky(keepNonOkResultsStickyForSec = 10)
    public class SampleHealthCheck implements HealthCheck {
    ...


## General purpose health checks available out-of-the-box 

The following checks are contained in bundle `org.apache.felix.healthcheck.generalchecks` and can be activated by simple configuration:

Check | PID | Factory | Description  
--- | --- | --- | ---
Framework Startlevel | org.apache.felix.hc.generalchecks.FrameworkStartCheck | no | Checks the OSGi framework startlevel - `targetStartLevel` allows to configure a target start level, `targetStartLevel.propName` can be used to read it from the framework/system properties. 
Services Ready | org.apache.felix.hc.generalchecks.ServicesCheck | yes | Checks for the existance of the given services. `services.list` can contain simple service names or filter expressions 
Components Ready | org.apache.felix.hc.generalchecks.DsComponentsCheck | yes | Checks for the existance of the given components. Use `components.list` to list required active components (use component names) 
Bundles Started | org.apache.felix.hc.generalchecks.BundlesStartedCheck | yes | Checks for started bundles - `includesRegex` and `excludesRegex` control what bundles are checked. 
Disk Space | org.apache.felix.hc.generalchecks.DiskSpaceCheck | yes | Checks for disk space usage at the given paths `diskPaths` and checks them against thresholds `diskUsedThresholdWarn` (default 90%) and diskUsedThresholdCritical (default 97%)
Memory | org.apache.felix.hc.generalchecks.MemoryCheck | no | Checks for Memory usage - `heapUsedPercentageThresholdWarn` (default 90%) and `heapUsedPercentageThresholdCritical` (default 99%) can be set to control what memory usage produces status `WARN` and `CRITICAL`
CPU | org.apache.felix.hc.generalchecks.CpuCheck | no | Checks for CPU usage - `cpuPercentageThresholdWarn` (default 95%) can be set to control what CPU usage produces status `WARN` (check never results in `CRITICAL`)
Thread Usage | org.apache.felix.hc.generalchecks.ThreadUsageCheck | no | Checks via `ThreadMXBean.findDeadlockedThreads()` for deadlocks and analyses the CPU usage of each thread via a configurable time period (`samplePeriodInMs` defaults to 200ms). Uses `cpuPercentageThresholdWarn` (default 95%) to `WARN` about high thread utilisation.   
JMX Attribute Check | org.apache.felix.hc.generalchecks.JmxAttributeCheck | yes | Allows to check an arbitrary JMX attribute (using the configured mbean `mbean.name`'s attribute `attribute.name`) against a given constraint `attribute.value.constraint`. Can check multiple attributes by providing additional config properties with numbers:  `mbean2.name`' `attribute2.name` and `attribute2.value.constraint`.
HttpRequestsCheck | org.apache.felix.hc.generalchecks.HttpRequestsCheck | yes | Allows to check a list of URLs against response code, response headers, timing, response content (plain content via RegEx or JSON via path expression).


## Executing Health Checks

Health Checks can be executed via a [webconsole plugin](#webconsole-plugin), the [health check servlet](#health-check-servlet) or via [JMX](#jmx-access-to-health-checks). `HealthCheck` services can be selected for execution based on their `hc.tags` multi-value service property. 

The `HealthCheckFilter` utility accepts positive and negative tag parameters, so that `osgi,-security` 
selects all `HealthCheck` having the `osgi` tag but not the `security` tag, for example.

For advanced use cases it is also possible to use the API directly by using the interface `org.apache.felix.hc.api.execution.HealthCheckExecutor`.

### Configuring the Health Check Executor
The health check executor can **optionally** be configured via service PID `org.apache.felix.hc.core.impl.executor.HealthCheckExecutorImpl`:

Property    | Type     | Default | Description  
----------- | -------- | ------ | ------------
`timeoutInMs`    | Long   | 2000ms | Timeout in ms until a check is marked as timed out
`longRunningFutureThresholdForCriticalMs` | Long | 300000ms (5min) | Threshold in ms until a check is marked as 'exceedingly' timed out and will marked CRITICAL instead of WARN only
`resultCacheTtlInMs` | Long | 2000ms | Result Cache time to live - results will be cached for the given time
`temporarilyAvailableGracePeriodInMs` | Long | 60000ms (10min) | After this configured period, health checks continously reporting `TEMPORARILY_UNAVAILABLE` are automatically turned into status `CRITICAL`


### JMX access to health checks
If the `org.apache.felix.hc.jmx` bundle is active, a JMX MBean is created for each `HealthCheck` which has the 
service property `hc.mbean.name` service property set. All health check MBeans are registered in the 
domain `org.apache.felix.healthcheck` with a type of `HealthCheck`.

The MBean gives access to the `Result` and the log, as shown on the screenshot below.   

### Health Check Servlet
The health check servlet allows to query the checks via http. It provides
similar features to the Web Console plugin described above, with output in HTML, JSON (plain or jsonp) and TXT (concise or verbose) formats (see HTML format rendering page for more documentation).

The Health Checks Servlet is disabled by default, to enable it create an OSGi configuration like

    PID = org.apache.felix.hc.core.impl.servlet.HealthCheckExecutorServlet
    servletPath = /system/health

which specifies the servlet's base path. That URL then returns an HTML page, by default with the results of all active health checks and
with instructions at the end of the page about URL parameters which can be used to select specific Health Checks and control their execution and output format.

Note that by design **the Health Checks Servlet doesn't do any access control by itself** to ensure it can detect unhealthy states of the authentication itself. Make sure the configured path is only accessible to relevant infrastructure and operations people. Usually all `/system/*` paths are only accessible from a local network and not routed to the Internet.

By default the HC servlet sends the CORS header `Access-Control-Allow-Origin: *` to allow for client-side browser integrations. The behaviour can be configured using the OSGi config property `cors.accessControlAllowOrigin` (a blank value disables the header).

### Webconsole plugin

If the `org.apache.felix.hc.webconsole` bundle is active, a webconsole plugin 
at `/system/console/healthcheck` allows for executing health checks, optionally selected
based on their tags (positive and negative selection, see the `HealthCheckFilter` mention above).

The DEBUG logs of health checks can optionally be displayed, and an option allows for showing only health checks that have a non-OK status.