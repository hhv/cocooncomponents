/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cocoon.transformation;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.avalon.framework.CascadingException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.cron.CocoonQuartzJobScheduler;
import org.apache.cocoon.components.cron.JobSchedulerEntry;
import org.apache.cocoon.components.cron.QueueAddJob;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.apache.excalibur.source.SourceParameters;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This transformer can list Quartz Cronjobs and add new cronjobs or delete them.
 *
 * This transformer triggers for element in the namespace "http://apache.org/cocoon/quartz/1.0".
 * 
 
 * Example XML input:
 * <pre>
 *   <quartz:list/>
 *   <quartz:add name="MyJob" uri="/myapp/myjob?query-param=value" cron="0 &#42;/2 * * *" />
 *      The uri attribute DOES NOT specify protocol, host and port name. The
 *      protocol is always "http" and the host and port are determined at
 *      runtime.
 *   <quartz:delete name="MyJob" />
 * </pre>
 * 
 * @author Huib Verweij (hhv@x-scale.nl)
 *
 */
public class QuartzTransformer extends AbstractSAXTransformer {

    public static final String QUARTZ_NAMESPACE_URI = "http://apache.org/cocoon/quartz/1.0";
    private static final String QUARTZ_PREFIX = "cron";
//    public static final String HTTP_NAMESPACE_URI = "http://www.w3.org/2006/http#";
    private static final String LIST_ELEMENT = "list";
    private static final String NAME_ATTR = "name";
    private static final String URI_ATTR = "uri";
    private static final String JOBNAME_ATTR = "job-name";
    private static final String JOBDESCRIPTION_ATTR = "job-description";
    private static final String CRON_ATTR = "cron";
    private static final String ADD_ELEMENT = "add";
    private static final String DELETE_ELEMENT = "delete";

    private static final String JOBS_ELEMENT = "jobs";
    private static final String JOB_ELEMENT = "job";
    private static final String SCHEDULE_ATTR = "schedule";
    private static final String NEXTTIME_ATTR = "next-time";
    private static final String ISRUNNING_ATTR = "is-running";

    
    private static final String COCOON_URI = "cocoon:";
    
    private String uri;
    private String name;
    private String cron;
    private String jobName;
    private String jobDescription;
    private String parameterName;
    private String parse;
    private boolean showErrors;
    private Map httpHeaders;
    private SourceParameters requestParameters;

    public QuartzTransformer() {
        this.defaultNamespaceURI = QUARTZ_NAMESPACE_URI;
    }

    public void setup(SourceResolver resolver, Map objectModel, String src,
            Parameters params) throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, params);
    }

    private String getAttribute(Attributes attr, String name, String defaultValue) {
        return (attr.getIndex(name) >= 0) ? attr.getValue(name) : defaultValue;
    }

    public void startTransformingElement(String uri, String name, String raw, Attributes attr)
            throws ProcessingException, IOException, SAXException {
        if (name.equals(LIST_ELEMENT)) {
        }
        if (name.equals(ADD_ELEMENT)) {
            this.uri = getAttribute(attr, URI_ATTR, null);
            if (this.uri == null) {
                throw new ProcessingException("The " + URI_ATTR + " attribute is mandatory for " + ADD_ELEMENT + " elements.");
            }
            this.name = getAttribute(attr, NAME_ATTR, null);
            if (this.name == null) {
                throw new ProcessingException("The " + NAME_ATTR + " attribute is mandatory for " + ADD_ELEMENT + " elements.");
            }
            this.cron = getAttribute(attr, CRON_ATTR, null);
            if (this.cron == null) {
                throw new ProcessingException("The " + CRON_ATTR + " attribute is mandatory for " + ADD_ELEMENT + " elements.");
            }
            this.jobName = getAttribute(attr, JOBNAME_ATTR, null);
            this.jobDescription = getAttribute(attr, JOBDESCRIPTION_ATTR, null);
        }
    }

    public void endTransformingElement(String uri, String name, String raw)
            throws ProcessingException, IOException, SAXException {
        if (name.equals(LIST_ELEMENT)) {
            try {
                listCronJobs();
            } catch (ServiceException ex) {
                Logger.getLogger(QuartzTransformer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (name.equals(ADD_ELEMENT)) {
            try {
                addCronJob(this.uri, this.name, this.cron);
            } catch (ParseException ex) {
                Logger.getLogger(QuartzTransformer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ServiceException ex) {
                Logger.getLogger(QuartzTransformer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    
    /**
     * Add a cron job to add a job to the QueueProcessorCronJob's queue.
     * @param uri The URI to call, must start with cocoon:.
     * @param name Name of the cronjob.
     * @param cron Cron specification, e.g. "0 0/10 0 0 0 ?"
     * @throws ProcessingException
     * @throws IOException
     * @throws SAXException
     * @throws ParseException
     * @throws ServiceException 
     */
    private void addCronJob(String uri, String name, String cron)
            throws ProcessingException, IOException, SAXException, ParseException, ServiceException {
//        if (!(uri.startsWith(COCOON_URI))) {
//            throw new ProcessingException("URI must start use cocoon: protocol.");
//        }
        
        CocoonQuartzJobScheduler cqjs = (CocoonQuartzJobScheduler) this.manager.
                lookup(CocoonQuartzJobScheduler.ROLE);
        
        Parameters parameters = new Parameters();
        parameters.setParameter(URI_ATTR, uri);
        parameters.setParameter(JOBNAME_ATTR, this.jobName);
        parameters.setParameter(JOBDESCRIPTION_ATTR, this.jobDescription);
        try {
            cqjs.addJob(name, QueueAddJob.ROLE, cron, false, parameters, null);
        } catch (CascadingException ex) {
            Logger.getLogger(QuartzTransformer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void listCronJobs() throws ServiceException, SAXException {
        CocoonQuartzJobScheduler cqjs = (CocoonQuartzJobScheduler) this.manager.
                lookup(CocoonQuartzJobScheduler.ROLE);
//        if (this.getLogger().isDebugEnabled()) {
        this.getLogger().info("org.apache.cocoon.components.cron.CocoonQuartzJobScheduler = " + cqjs);
//        }
        String[] jobs = cqjs.getJobNames();
        xmlConsumer.startElement(QUARTZ_NAMESPACE_URI, JOBS_ELEMENT,
                String.format("%s:%s", QUARTZ_PREFIX, JOBS_ELEMENT),
                EMPTY_ATTRIBUTES);
        for (String job : jobs) {
            this.getLogger().info("job = " + job);

            JobSchedulerEntry entry = cqjs.getJobSchedulerEntry(job);

            AttributesImpl attr = new AttributesImpl();
            attr.addAttribute("", NAME_ATTR, NAME_ATTR, "CDATA", job);

            attr.addAttribute("", SCHEDULE_ATTR, SCHEDULE_ATTR, "CDATA", entry.getSchedule());
            attr.addAttribute("", NEXTTIME_ATTR, NEXTTIME_ATTR, "CDATA", entry.getNextTime().toString());
            attr.addAttribute("", ISRUNNING_ATTR, ISRUNNING_ATTR, "CDATA", entry.isRunning() ? "true" : "false");

            xmlConsumer.startElement(QUARTZ_NAMESPACE_URI, JOB_ELEMENT,
                    String.format("%s:%s", QUARTZ_PREFIX, JOB_ELEMENT),
                    attr);
            xmlConsumer.endElement(QUARTZ_NAMESPACE_URI, JOB_ELEMENT,
                    String.format("%s:%s", QUARTZ_PREFIX, JOB_ELEMENT));
        }
        xmlConsumer.endElement(QUARTZ_NAMESPACE_URI, JOBS_ELEMENT,
                String.format("%s:%s", QUARTZ_PREFIX, JOBS_ELEMENT));
    }

}
