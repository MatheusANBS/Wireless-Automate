package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.compat.ftbteams.FtbTeams;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Ponte para os times do FTB Teams sem carregar classes dele quando não está instalado. Quem chama confere
 * {@link #LOADED} (ou aceita o resultado vazio); {@link FtbTeams} só é carregada pela JVM quando um destes métodos
 * roda com o FTB presente. Só times de grupo (party) contam: o time pessoal de cada jogador nunca é um time da rede.
 */
public final class TeamAccess {
    /** O FTB Teams está instalado: a seção de times existe nas telas e os times de uma rede valem. */
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("ftbteams");

    /**
     * Um time para mostrar.
     *
     * @param members quantos jogadores o time tem
     * @param gone    o time não existe mais (foi desfeito): não dá acesso a ninguém
     */
    public record TeamInfo(UUID id, String name, int members, boolean gone) {
    }

    /** Os membros de cada time já consultados dentro de {@link #openScope}; {@code null} fora dela. */
    private static @Nullable Map<UUID, Set<UUID>> scope;

    /** O que {@link #openScope} devolve: fechar volta a consultar o FTB a cada pergunta. */
    public static final class Scope implements AutoCloseable {
        private Scope() {
        }

        @Override
        public void close() {
            scope = null;
        }
    }

    private static final Scope SCOPE = new Scope();

    /**
     * Dentro do try-with-resources, {@link #isMember} consulta cada time do FTB uma vez (a montagem do snapshot do
     * Tablet pergunta o acesso de cada nó, e o FTB monta um conjunto novo a cada consulta). Só a thread do servidor.
     */
    public static Scope openScope() {
        if (LOADED) {
            scope = new HashMap<>();
        }
        return SCOPE;
    }

    /** O jogador é membro do time. Um time desfeito ou que não é de grupo não tem membros. */
    public static boolean isMember(UUID team, UUID player) {
        if (!LOADED) {
            return false;
        }
        Map<UUID, Set<UUID>> open = scope;
        return open == null ? FtbTeams.isMember(team, player)
                : open.computeIfAbsent(team, FtbTeams::members).contains(player);
    }

    /** O time para mostrar; {@code null} sem o FTB. Um time que sumiu volta com {@code gone}. */
    public static @Nullable TeamInfo info(UUID team) {
        return LOADED ? FtbTeams.info(team) : null;
    }

    /** Todos os times de grupo válidos, para as sugestões; vazio sem o FTB. Uma lista nova a cada chamada. */
    public static List<TeamInfo> teams() {
        return LOADED ? FtbTeams.teams() : List.of();
    }

    private TeamAccess() {
    }
}
