package org.hyperic.hq.inventory.dao;

import java.util.List;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hyperic.hq.inventory.domain.PropertyType;
import org.hyperic.hq.inventory.domain.ResourceType;
import org.hyperic.hq.product.Plugin;
import org.springframework.data.graph.neo4j.finder.FinderFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class Neo4jResourceTypeDao implements ResourceTypeDao {

    @Resource
    private FinderFactory finderFactory;
    
    @PersistenceContext
    protected EntityManager entityManager;

    @Transactional(readOnly = true)
    public ResourceType findById(Integer id) {
        if (id == null) return null;
        
        ResourceType result = entityManager.find(ResourceType.class, id);
        
        // TODO workaround to trigger Neo4jNodeBacking's around advice for the getter
        if(result != null) {
            result.getId();
        }
        
        return result;
    }
    
    @Transactional(readOnly = true)
    public List<ResourceType> findAll() {
        List<ResourceType> result =  entityManager.createQuery("select o from ResourceType o",ResourceType.class).getResultList();
        
        // TODO workaround to trigger Neo4jNodeBacking's around advice for the getter
        for (ResourceType resourceType : result) {
            resourceType.getId();
        }
        
        return result;
    }

    
    @Transactional(readOnly = true)
    public List<ResourceType> find(Integer firstResult, Integer maxResults) {
        List<ResourceType> result = entityManager.createQuery("select o from ResourceType o",ResourceType.class)
            .setFirstResult(firstResult)
            .setMaxResults(maxResults)
            .getResultList();
        
        // TODO workaround to trigger Neo4jNodeBacking's around advice for the getter
        for (ResourceType resourceType : result) {
            resourceType.getId();
        }
        
        return result;
    }

    @Transactional(readOnly = true)
    public Long count() {
        return (Long) entityManager.createQuery("select count(o) from ResourceType o").getSingleResult();
    }
    
    @Transactional(readOnly = true)
    public ResourceType findRoot() {
        return findById(1);
    }
    
    @Transactional(readOnly = true)
    public ResourceType findByName(String name) {
        // Can't do JPA-style queries on property values that are only in graph
        ResourceType type = finderFactory.createNodeEntityFinder(ResourceType.class)
            .findByPropertyValue(null, "name",name);

        if (type != null) {
            type.getId();
        }

        return type;
    }
    
    @Transactional
    public PropertyType createPropertyType(String name,Class<?> type) {
        PropertyType propType = new PropertyType();
        propType.setName(name);
        entityManager.persist(propType);
        propType.getId();
        //TODO care about formalized type?
        return propType;
    }
    
    @Transactional
    public ResourceType create(String name, Plugin plugin) {
        ResourceType resourceType = new ResourceType();
        resourceType.setName(name);
        entityManager.persist(resourceType);
        resourceType.getId();
        resourceType.setPlugin(plugin);
        return resourceType;
    }
    
    @Transactional
    public ResourceType create(String name) {
        ResourceType resourceType = new ResourceType();
        resourceType.setName(name);
        entityManager.persist(resourceType);
        resourceType.getId();
        return resourceType;
    }

}
