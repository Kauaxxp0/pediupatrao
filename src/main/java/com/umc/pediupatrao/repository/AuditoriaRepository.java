package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repositório "somente inclusão": estende o marcador {@link Repository} em vez de
 * MongoRepository, expondo apenas insert e consultas. Não há save (update),
 * delete nem deleteAll, portanto a aplicação não consegue alterar ou apagar registros.
 */
public interface AuditoriaRepository extends Repository<RegistroAuditoria, String> {

    /** insert falha se o id já existir, nunca sobrescreve um registro. */
    <S extends RegistroAuditoria> S insert(S registro);

    Optional<RegistroAuditoria> findTopByOrderBySequenciaDesc();

    List<RegistroAuditoria> findAllByOrderBySequenciaAsc();
}
