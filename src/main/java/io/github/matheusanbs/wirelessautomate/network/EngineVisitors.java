package io.github.matheusanbs.wirelessautomate.network;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;

/**
 * Os {@link EngineVisitor} do {@link NetworkManager}, com cursor próprio, como as origens: uma volta pelos acordados
 * enquanto houver orçamento, começando depois do último que moveu ({@link SourceCursor}), e voltas extras para quem
 * parou num teto ({@link EngineVisitor#MORE}) enquanto a visita seguinte (estimada pela anterior) couber.
 *
 * <p>Sem visitante, o laço do gerenciador não entra aqui. Se as portas gastam o orçamento antes da volta dos
 * visitantes, eles ficam com fome ({@link #starve}) e a volta do tick seguinte vem antes das portas ({@link #fed}
 * devolve a vez a elas depois): sob carga as duas voltas se alternam na frente, e nenhuma fica sem visita.
 *
 * <p>Tirar um visitante de dentro de uma visita (o dono saiu do mundo) só o marca; a lista é compactada no fim da
 * volta. Só a thread do servidor mexe aqui.
 */
final class EngineVisitors {
    private final ArrayList<Entry> list = new ArrayList<>();
    private final Reference2ObjectOpenHashMap<EngineVisitor, Entry> byVisitor = new Reference2ObjectOpenHashMap<>();
    private final ArrayList<Entry> hungry = new ArrayList<>();
    private int cursor;
    /** Uma volta em andamento: tirar só marca ({@link Entry#removed}). */
    private boolean passing;
    private boolean holes;
    private boolean starved;

    boolean isEmpty() {
        return list.isEmpty();
    }

    /** A última volta parou sem orçamento, ou as portas não deixaram orçamento para ela: a próxima vem antes das portas. */
    boolean starved() {
        return starved;
    }

    /** As portas gastaram o orçamento antes da volta dos visitantes. */
    void starve() {
        starved = !list.isEmpty();
    }

    /** A volta veio antes das portas: o tick seguinte volta a começar por elas. */
    void fed() {
        starved = false;
    }

    void add(EngineVisitor visitor) {
        if (byVisitor.containsKey(visitor)) {
            return;
        }
        Entry entry = new Entry(visitor);
        byVisitor.put(visitor, entry);
        list.add(entry);
    }

    void remove(EngineVisitor visitor) {
        Entry entry = byVisitor.remove(visitor);
        if (entry == null) {
            return;
        }
        entry.removed = true;
        if (passing) {
            holes = true;
            return;
        }
        int index = list.indexOf(entry);
        list.remove(index);
        if (index < cursor) {
            cursor--;
        }
        if (list.isEmpty()) {
            starved = false;
        }
    }

    boolean contains(EngineVisitor visitor) {
        return byVisitor.containsKey(visitor);
    }

    /** Acorda {@code visitor} para a próxima volta, seja qual for o {@code wakeAt} dele. */
    void wake(EngineVisitor visitor) {
        Entry entry = byVisitor.get(visitor);
        if (entry != null) {
            entry.woken = true;
        }
    }

    /**
     * Uma volta pelos acordados no tick {@code now}, enquanto {@code budget} tiver tempo; com {@code visitedAlready}
     * falso, a primeira visita entra mesmo sem tempo (pelo menos uma por tick). Devolve o último {@code nanoTime}.
     */
    long pass(long now, TickBudget budget, boolean visitedAlready) {
        int count = list.size();
        int start = cursor < count ? cursor : 0;
        int index = start;
        int lastMover = -1;
        boolean visited = visitedAlready;
        hungry.clear();
        passing = true;
        try {
            for (int step = 0; step < count; step++) {
                Entry entry = list.get(index);
                if (!entry.removed && (entry.woken || entry.visitor.wakeAt() <= now)) {
                    long time = System.nanoTime();
                    if (visited && !budget.hasTime(time)) {
                        cursor = index;
                        starved = true;
                        hungry.clear();
                        return time;
                    }
                    visited = true;
                    // Antes da visita: um wakeVisitor de dentro dela vale para a volta seguinte.
                    entry.woken = false;
                    int result = entry.visitor.visit(now);
                    if ((result & EngineVisitor.MOVED) != 0) {
                        lastMover = index;
                    }
                    if ((result & EngineVisitor.MORE) != 0) {
                        entry.visitNanos = System.nanoTime() - time;
                        hungry.add(entry);
                    }
                }
                index = index + 1 == count ? 0 : index + 1;
            }
            cursor = SourceCursor.next(start, lastMover, count);
            starved = false;
            while (!hungry.isEmpty()) {
                int kept = 0;
                for (int i = 0, n = hungry.size(); i < n; i++) {
                    Entry entry = hungry.get(i);
                    long time = System.nanoTime();
                    if (entry.removed || !budget.fits(time, entry.visitNanos)) {
                        continue;
                    }
                    entry.woken = false;
                    if ((entry.visitor.visit(now) & EngineVisitor.MORE) != 0) {
                        entry.visitNanos = System.nanoTime() - time;
                        hungry.set(kept++, entry);
                    }
                }
                hungry.subList(kept, hungry.size()).clear();
            }
            return System.nanoTime();
        } finally {
            passing = false;
            if (holes) {
                compact();
            }
        }
    }

    /** Tira da lista os marcados numa volta, com o cursor apontando para o mesmo visitante. */
    private void compact() {
        holes = false;
        int kept = 0;
        int newCursor = 0;
        for (int i = 0, n = list.size(); i < n; i++) {
            Entry entry = list.get(i);
            if (i == cursor) {
                newCursor = kept;
            }
            if (!entry.removed) {
                list.set(kept++, entry);
            }
        }
        list.subList(kept, list.size()).clear();
        cursor = newCursor;
        if (list.isEmpty()) {
            starved = false;
        }
    }

    private static final class Entry {
        final EngineVisitor visitor;
        /** Acordado pelo {@code wakeVisitor}, sem esperar o {@code wakeAt}. */
        boolean woken;
        boolean removed;
        /** Quanto durou a última visita que parou num teto, para as voltas extras. */
        long visitNanos;

        Entry(EngineVisitor visitor) {
            this.visitor = visitor;
        }
    }
}
