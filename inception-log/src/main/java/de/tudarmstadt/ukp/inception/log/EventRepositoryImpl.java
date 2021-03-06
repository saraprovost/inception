/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.log;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.inception.log.model.LoggedEvent;

@Component
public class EventRepositoryImpl
    implements EventRepository
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    @PersistenceContext
    private EntityManager entityManager;

    public EventRepositoryImpl()
    {
    }

    public EventRepositoryImpl(EntityManager aEntityManager)
    {
        entityManager = aEntityManager;
    }

    @Override
    @Transactional
    public void create(LoggedEvent aEvent)
    {
        log.info("{}", aEvent);
        entityManager.persist(aEvent);
    }

    @Override
    @Transactional
    public List<LoggedEvent> listLoggedEvents(Project aProject, String aUsername, String aEventType,
            int aSize, long recommenderId)
    {
        String query = String.join("\n",
                "FROM LoggedEvent WHERE ",
                "user=:user AND ",
                "project = :project AND ",
                "event = :event AND ",
                "details LIKE :details ",
                "ORDER BY created DESC");

        return entityManager.createQuery(query, LoggedEvent.class)
                .setParameter("user", aUsername)
                .setParameter("project", aProject.getId())
                .setParameter("event", aEventType)
                .setParameter("details", "%\"recommenderId\":" + recommenderId + "%")
                .setMaxResults(aSize)
                .getResultList();
    }
    
    @Override
    @Transactional
    public void forEachLoggedEvent(Project aProject, Consumer<LoggedEvent> aConsumer)
    {
        // Set up data source
        String query = String.join("\n",
                "FROM LoggedEvent WHERE ",
                "project = :project ",
                "ORDER BY id");
        TypedQuery<LoggedEvent> typedQuery = entityManager.createQuery(query, LoggedEvent.class)
                .setParameter("project", aProject.getId());

        try (Stream<LoggedEvent> eventStream = typedQuery.getResultStream()) {
            eventStream.forEach(aConsumer);
        }
    }
}
