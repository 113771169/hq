package org.hyperic.hq.events.server.session;

import java.util.List;
import java.util.Set;

import org.hyperic.hq.events.shared.RegisteredTriggerValue;
/**
 * DAO for interacting with {@link RegisteredTrigger}s
 * @author jhickey
 *
 */
public interface TriggerDAOInterface {

    RegisteredTrigger create(RegisteredTriggerValue createInfo);

    void deleteAlertDefinition(AlertDefinition def);

    List<RegisteredTrigger> findAll();

    List<RegisteredTrigger> findByAlertDefinitionId(Integer id);

    RegisteredTrigger findById(Integer id);

    RegisteredTrigger get(Integer id);

    void removeTriggers(AlertDefinition def);

    Set<RegisteredTrigger> findAllEnabledTriggers();
}
