package com.vigia.service;

import com.vigia.auth.UserRepository;
import com.vigia.config.MonitorProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * A "vitrine": os serviços públicos de uma única conta (configurada em SHOWCASE_OWNER_EMAIL)
 * aparecem na página pública e no painel de todos os usuários, só para leitura.
 * Os serviços dos demais usuários ficam visíveis apenas para eles mesmos, o que impede que
 * qualquer pessoa que crie uma conta publique algo na página de status.
 *
 * Sem SHOWCASE_OWNER_EMAIL (uso local, um único usuário), qualquer serviço marcado como público aparece.
 */
@Component
public class Showcase {

    private final UserRepository users;
    private final String ownerEmail;

    private final int maxServicesPerUser;

    public Showcase(UserRepository users, MonitorProperties properties) {
        this.users = users;
        this.ownerEmail = properties.showcaseOwnerEmail() == null ? "" : properties.showcaseOwnerEmail().trim();
        this.maxServicesPerUser = properties.limits() == null ? 0 : properties.limits().maxServicesPerUser();
    }

    public boolean isOwner(Long userId) {
        return ownerId().filter(userId::equals).isPresent();
    }

    /**
     * Quantos serviços a conta pode ter, ou nulo se não houver limite. O limite impede que uma conta
     * qualquer faça o servidor verificar milhares de URLs; o dono da vitrine não tem limite.
     */
    public Integer serviceLimit(Long userId) {
        return maxServicesPerUser <= 0 || isOwner(userId) ? null : maxServicesPerUser;
    }

    public boolean configured() {
        return !ownerEmail.isEmpty();
    }

    /** Id da conta dona da vitrine, se configurada e já cadastrada. */
    public Optional<Long> ownerId() {
        if (!configured()) {
            return Optional.empty();
        }
        return users.findByEmailIgnoreCase(ownerEmail).map(u -> u.getId());
    }

    /** Pode marcar serviços como públicos? Só o dono da vitrine (ou todos, se não houver vitrine). */
    public boolean canPublish(Long userId) {
        return !configured() || isOwner(userId);
    }
}
