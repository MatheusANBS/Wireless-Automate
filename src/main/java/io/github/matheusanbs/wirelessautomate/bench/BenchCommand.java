package io.github.matheusanbs.wirelessautomate.bench;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * {@code /wa bench}: roda um cenário do benchmark neste servidor (permissão 4). Monta a cena longe
 * do spawn ({@link BenchScene#ORIGIN}), mede e desmonta; o relatório sai no chat, no log e em
 * {@code wirelessautomate-bench/}. Feito para um mundo de teste: a área da cena é sobrescrita.
 */
public final class BenchCommand {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** O nó {@code bench}, para pendurar no {@code /wa}. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("bench")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("run")
                        .then(Commands.argument("scenario", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(BenchScenario.values()).map(s -> s.id), builder))
                                .executes(context -> run(context, null, 3, BenchStorage.VANILLA))
                                .then(Commands.argument("n", IntegerArgumentType.integer(2, 10_000))
                                        .executes(context -> run(context, IntegerArgumentType.getInteger(context, "n"), 3,
                                                BenchStorage.VANILLA))
                                        .then(Commands.argument("reps", IntegerArgumentType.integer(1, 50))
                                                .executes(context -> run(context, IntegerArgumentType.getInteger(context, "n"),
                                                        IntegerArgumentType.getInteger(context, "reps"), BenchStorage.VANILLA))
                                                .then(Commands.argument("storage", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                Arrays.stream(BenchStorage.values()).map(s -> s.id), builder))
                                                        .executes(context -> run(context,
                                                                IntegerArgumentType.getInteger(context, "n"),
                                                                IntegerArgumentType.getInteger(context, "reps"),
                                                                BenchStorage.byId(StringArgumentType.getString(context, "storage"))))
                                                        .then(Commands.argument("transport", StringArgumentType.word())
                                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                        Arrays.stream(BenchTransport.values()).map(t -> t.id), builder))
                                                                .executes(context -> run(context,
                                                                        IntegerArgumentType.getInteger(context, "n"),
                                                                        IntegerArgumentType.getInteger(context, "reps"),
                                                                        BenchStorage.byId(StringArgumentType.getString(context, "storage")),
                                                                        BenchTransport.byId(StringArgumentType.getString(context, "transport"))))))))))
                .then(Commands.literal("stop")
                        .executes(context -> {
                            BenchRunner.stop();
                            context.getSource().sendSuccess(() -> Component.literal("Benchmark interrompido."), true);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(context -> {
                            String status = BenchRunner.status();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    status == null ? "Nenhum benchmark rodando." : status), false);
                            return status == null ? 0 : 1;
                        }));
    }

    private static int run(CommandContext<CommandSourceStack> context, Integer nodes, int reps, BenchStorage storage) {
        return run(context, nodes, reps, storage, BenchTransport.WA);
    }

    private static int run(CommandContext<CommandSourceStack> context, Integer nodes, int reps, BenchStorage storage,
            BenchTransport transport) {
        CommandSourceStack source = context.getSource();
        if (transport == null) {
            source.sendFailure(Component.literal("Transporte desconhecido: use wa, wa-full, ln, ln-rr ou ln-async."));
            return 0;
        }
        BenchScenario scenario = BenchScenario.byId(StringArgumentType.getString(context, "scenario"));
        if (scenario == null) {
            source.sendFailure(Component.literal("Cenário desconhecido. Opções: "
                    + String.join(", ", Arrays.stream(BenchScenario.values()).map(s -> s.id).toList())));
            return 0;
        }
        if (storage == null) {
            source.sendFailure(Component.literal("Armazenamento desconhecido: use vanilla ou soph."));
            return 0;
        }
        String problem = BenchRunner.problem(scenario, storage, transport);
        if (problem != null) {
            source.sendFailure(Component.literal(problem));
            return 0;
        }
        if (BenchRunner.busy()) {
            source.sendFailure(Component.literal("Já há um benchmark rodando: " + BenchRunner.status()));
            return 0;
        }
        BenchRun.Job job = new BenchRun.Job(scenario, nodes == null ? scenario.defaultNodes : nodes, reps, storage, transport);
        Path file = Path.of("wirelessautomate-bench", LocalDateTime.now().format(FILE_TIME) + "-" + scenario.id + ".md");
        file.getParent().toFile().mkdirs();
        BenchRunner.submit(List.of(job), line -> source.sendSuccess(() -> Component.literal(line), false), file);
        source.sendSuccess(() -> Component.literal("Benchmark " + job.label() + " começando; relatório em " + file), true);
        return 1;
    }

    private BenchCommand() {
    }
}
