/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
open module co.elastic.apm.spring.boot.moduleexample {
	// compile time modules required for Hibernate
	requires java.persistence;
	requires org.hibernate.orm.core;
	requires spring.beans;
	requires spring.boot;
	requires spring.boot.autoconfigure;
	requires spring.core;
	requires spring.context;
	requires spring.data.commons;
	requires spring.data.jpa;
	requires spring.tx;

	// runtime time modules required for Hibernate
	requires net.bytebuddy;
	requires com.fasterxml.classmate;
	requires java.xml.bind;

	// compile time modules required for Tomcat
	requires org.apache.tomcat.embed.core;
	requires spring.aop;
	requires spring.web;
	requires spring.webmvc;

	// runtime time modules required for Tomcat

	// APM Agent
	requires jdk.unsupported;
}
