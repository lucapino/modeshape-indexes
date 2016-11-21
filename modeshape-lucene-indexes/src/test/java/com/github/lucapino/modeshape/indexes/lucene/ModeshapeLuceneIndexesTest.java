/* 
 * Copyright 2016 Tagliani.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.lucapino.modeshape.indexes.lucene;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryManager;
import org.apache.commons.lang3.StringUtils;
import org.modeshape.common.collection.Problem;
import org.modeshape.common.collection.Problems;
import org.modeshape.common.util.FileUtil;
import org.modeshape.jcr.ModeShapeEngine;
import org.modeshape.jcr.RepositoryConfiguration;
import org.modeshape.jcr.api.Workspace;
import org.modeshape.jcr.api.index.IndexColumnDefinitionTemplate;
import org.modeshape.jcr.api.index.IndexDefinition;
import org.modeshape.jcr.api.index.IndexDefinitionTemplate;
import org.modeshape.jcr.api.index.IndexManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 *
 * @author Tagliani
 */
@Test
public class ModeshapeLuceneIndexesTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModeshapeLuceneIndexesTest.class);

    private final ModeShapeEngine engine = new ModeShapeEngine();
    private Repository repository;
    private Session session;
    private IndexManager indexManager;
    private Workspace workspace;

    @BeforeClass
    public void start() {
        // Clear repository folder
        FileUtil.delete("target/repoLucene");
    }
    
    private void logHeader(String message) {
        message = "* " + message + " *";
        String filler = StringUtils.leftPad("", message.length(), "*");
        LOGGER.info(filler);
        LOGGER.info(message);
        LOGGER.info(filler);
    }

    @Test
    public void testIndexNotPersisting() throws Exception {
        // 0. Start engine and repository
        startEngineAndRepository();
        // 1. Create indexes
        createIndexes();
        // 2. Create nodes to be indexed
        addContent();
        // 3. Display index
        displayIndexes();
        // 4. Show query plan
        showQueryPlan();
        // 5. Shutdown engine
        stopEngine();
        // 6. Start engine and repository
        startEngineAndRepository();
        // 7. Display index
        displayIndexes();
        // 8. Show query plan
        showQueryPlan();
        // 9. Shutdown engine
        stopEngine();
    }

    private void startEngineAndRepository() throws Exception {
        logHeader("Start Engine and Repository");
        // Start the engine ...
        engine.start();

        // Load the configuration for a repository via the classloader (can also use path to a file)...
        URL url = this.getClass().getClassLoader().getResource("repositoryLucene.json");
        RepositoryConfiguration config = RepositoryConfiguration.read(url);

        // Verify the configuration for the repository ...
        Problems problems = config.validate();
        if (problems.hasErrors()) {
            LOGGER.error("Problems starting the engine.");
            for (Problem problem : problems) {
                LOGGER.error(problem.toString());
            }
            System.exit(-1);
        }

        // Deploy the repository ...
        repository = engine.deploy(config);

        // Get new session
        session = repository.login();

        // get workspace
        workspace = (Workspace) session.getWorkspace();

        // register test namespace
        workspace.getNamespaceRegistry().registerNamespace("my", "http://my");

        // get indexManager
        indexManager = workspace.getIndexManager();

    }

    private void createIndexes() throws Exception {
        logHeader("Create indexes");
        String workspaceName = "*";

        Map<String, Integer> columns = new HashMap<>();
        columns.put("jcr:name", PropertyType.NAME);
        createCustomIndex(indexManager, workspaceName, "nodesbyname", IndexDefinition.IndexKind.VALUE, true, "nt:base", columns);

        columns.clear();
        columns.put("mode:localName", PropertyType.STRING);
        createCustomIndex(indexManager, workspaceName, "nodesbylocalname", IndexDefinition.IndexKind.VALUE, true, "nt:base", columns);

        columns.clear();
        columns.put("mode:depth", PropertyType.LONG);
        createCustomIndex(indexManager, workspaceName, "nodesbydepth", IndexDefinition.IndexKind.VALUE, true, "nt:base", columns);

        columns.clear();
        columns.put("jcr:path", PropertyType.PATH);
        createCustomIndex(indexManager, workspaceName, "nodesbypath", IndexDefinition.IndexKind.VALUE, true, "nt:base", columns);

        columns.clear();
        columns.put("my:testProperty", PropertyType.STRING);
        createCustomIndex(indexManager, workspaceName, "nodesbymyproperty", IndexDefinition.IndexKind.VALUE, true, "nt:base", columns);
    }

    private void createCustomIndex(IndexManager indexManager, String workspaceName, String indexName, IndexDefinition.IndexKind indexKind, boolean synchronous, String nodeType, Map<String, Integer> columns) throws RepositoryException {
        IndexDefinitionTemplate indexDefinitionTemplate = indexManager.createIndexDefinitionTemplate();
        // we suppose there's only an index provider
        indexDefinitionTemplate.setProviderName("lucene");
        indexDefinitionTemplate.setWorkspace(workspaceName);
        indexDefinitionTemplate.setAllWorkspaces();
        indexDefinitionTemplate.setKind(indexKind);
        indexDefinitionTemplate.setSynchronous(synchronous);
        indexDefinitionTemplate.setNodeTypeName(nodeType);
        indexDefinitionTemplate.setName(indexName);
        for (String columnName : columns.keySet()) {
            IndexColumnDefinitionTemplate indexColumnDefinitionTemplate = indexManager.createIndexColumnDefinitionTemplate();
            indexColumnDefinitionTemplate.setPropertyName(columnName);
            indexColumnDefinitionTemplate.setColumnType(columns.get(columnName));
            indexDefinitionTemplate.setColumnDefinitions(indexColumnDefinitionTemplate);
        }
        indexManager.registerIndex(indexDefinitionTemplate, true);
    }

    private void displayIndexes() {
        logHeader("Display indexes");
        Map<String, IndexDefinition> indexDefinitions = indexManager.getIndexDefinitions();

        for (IndexDefinition def : indexDefinitions.values()) {
            String indexName = def.getName();
            IndexManager.IndexStatus indexStatus = indexManager.getIndexStatus("lucene", indexName, "default");
            Assert.assertEquals(indexStatus, IndexManager.IndexStatus.ENABLED);
            LOGGER.info("Index {} status: {}", indexName, indexStatus.toString());
        }
    }

    private void stopEngine() throws Exception {
        logHeader("Stop engine");
        // First shutdown the engine
        Future<Boolean> shutdown = engine.shutdown();
        // wait few seconds
        shutdown.get();
    }

    private void showQueryPlan() throws Exception {
        logHeader("Show query plan");

        QueryManager queryManager = session.getWorkspace().getQueryManager();
        javax.jcr.query.Query query = queryManager.createQuery("SELECT * FROM [nt:base] AS base WHERE base.[my:testProperty] ='Test'", javax.jcr.query.Query.JCR_SQL2);
        org.modeshape.jcr.api.query.Query msQuery = (org.modeshape.jcr.api.query.Query) query;

        // Get the query plan without executing it ...
        org.modeshape.jcr.api.query.QueryResult result = msQuery.explain();
        String plan = result.getPlan();
        LOGGER.info("Execution plan:\n {}", plan);

        queryManager = session.getWorkspace().getQueryManager();
        query = queryManager.createQuery("SELECT * FROM [nt:base] AS base WHERE base.[jcr:name] ='Test'", javax.jcr.query.Query.JCR_SQL2);
        msQuery = (org.modeshape.jcr.api.query.Query) query;

        // Get the query plan without executing it ...
        result = msQuery.explain();
        plan = result.getPlan();
        LOGGER.info("Execution plan:\n {}", plan);

    }

    private void addContent() throws Exception {
        logHeader("Add content");
        // Get the root node ...
        Node root = session.getRootNode();
        assert root != null;
        for (int i = 0; i < 100; i++) {
            Node newNode = root.addNode("test-" + UUID.randomUUID().toString());
            newNode.setProperty("my:property", UUID.randomUUID().toString());
        }
        session.save();

    }

}
