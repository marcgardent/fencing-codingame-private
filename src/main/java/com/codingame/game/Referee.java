package com.codingame.game;

import com.codingame.game.models.*;
import com.codingame.game.views.MainView;
import com.codingame.gameengine.core.AbstractPlayer.TimeoutException;
import com.codingame.gameengine.core.AbstractReferee;
import com.codingame.gameengine.core.GameManager;
import com.codingame.gameengine.core.MultiplayerGameManager;
import com.codingame.gameengine.module.tooltip.TooltipModule;
import com.google.inject.Inject;

import java.util.Random;

public class Referee extends AbstractReferee implements MatchObserver {
    @Inject
    private MultiplayerGameManager<Player> gameManager;
    @Inject
    private MainView view;

    @Inject
    TooltipModule tooltips;

    private GameModel state;
    private MatchModel match;
    private Random random;
    private int leagueId;

    private static String formatDelta(int d) {
        return "<constant>" + (d > 0 ? "+" + d : Integer.toString(d)) + "</constant>";
    }

    private static String formatQuantity(int d) {
        return "<constant>" + d + "</constant>";
    }

    @Override
    public void init() {
        exportAutoDoc();

        random = new Random(gameManager.getSeed());
        leagueId = gameManager.getLeagueLevel() - 1;
        match = new MatchModel(this);
        state = match.getState();


        Player playerA = gameManager.getPlayer(0);
        Player playerB = gameManager.getPlayer(1);

        view.init(state, playerA, playerB);

        gameManager.setFrameDuration(200);
        gameManager.setMaxTurns(MatchModel.MAX_TICK / 2);
    }

    @Override
    public void gameTurn(int turn) {

        if (state.restart) {
            state = match.restart();
            view.restart();
        } else {
            Player playerA = gameManager.getPlayer(0);
            Player playerB = gameManager.getPlayer(1);

            long timeoutA = playerA.sendInputs(state.teamA, state.teamB);
            long timeoutB = playerB.sendInputs(state.teamB, state.teamA);

//            gameManager.addToGameSummary(playerA.getNicknameToken() + "=" + timeoutA);
//            gameManager.addToGameSummary(playerB.getNicknameToken() + "=" + timeoutB);

            ActionType A = playerTurn(playerA, state.teamA, state.teamB);
            ActionType B = playerTurn(playerB, state.teamB, state.teamA);

            if (A == null && B == null) {
                playerA.setScore(-20);
                playerB.setScore(-20);
                endGame();
            } else if (A == null) {
                playerA.setScore(-20);
                playerB.setScore(20);
                endGame();
            } else if (B == null) {
                playerA.setScore(20);
                playerB.setScore(-20);
                endGame();
            } else {
                state = match.tick(A, B);
                view.tick();

                String msgA = String.join(", ", state.teamA.messages);
                String msgB = String.join(", ", state.teamB.messages);

//                gameManager.addToGameSummary(String.format("%s:%n", playerA.getNicknameToken()));
//                System.out.printf("%s:%n", playerA.getNicknameToken());
//                for (String msg : state.teamA.messages) {
//                    gameManager.addToGameSummary(String.format("%s%n", msg));
//                    System.out.printf("%s%n", msg);
//                }
//
//                gameManager.addToGameSummary(String.format("%s:%n", playerB.getNicknameToken()));
//                System.out.printf("%s:%n", playerB.getNicknameToken());
//                for (String msg : state.teamB.messages) {
//                    gameManager.addToGameSummary(String.format("%s%n", msg));
//                    System.out.printf("%s%n", msg);
//                }
            }
        }
    }

    private ActionType playerTurn(Player player, TeamModel me, TeamModel you) {
        try {
            final ActionType action = player.getAction(leagueId);
            gameManager.addToGameSummary(
                    String.format("%s played %s",
                            player.getNicknameToken(),
                            action.name()));
            return action;
        } catch (NumberFormatException e) {
            player.deactivate("Wrong output, excepted:integer!");
        } catch (TimeoutException e) {
            gameManager.addToGameSummary(GameManager.formatErrorMessage(player.getNicknameToken() + " timeout!"));
            player.deactivate(player.getNicknameToken() + " timeout!");
        } catch (InvalidAction e) {
            player.deactivate(e.getMessage());
        }
        return null;
    }

    private void endGame() {
        setScore();
        gameManager.endGame();
    }

    private void setScore() {
        Player playerA = gameManager.getPlayer(0);
        Player playerB = gameManager.getPlayer(1);
        boolean nonCombativityPenality = state.teamA.score == 0 && state.teamB.score == 0;
        boolean penalityA = playerA.isActive() && state.teamA.player.energy >= 0 && !nonCombativityPenality;
        boolean penalityB = playerA.isActive() && state.teamB.player.energy >= 0 && !nonCombativityPenality;
        playerA.setScore(penalityA ? (state.teamA.score - state.teamB.score) : -20);
        playerB.setScore(penalityB ? (state.teamB.score - state.teamA.score) : -20);

        gameManager.addToGameSummary(GameManager.formatSuccessMessage(
                "Final result: " + playerA.getNicknameToken() + "(" + playerA.getScore() + "), "
                        + playerB.getNicknameToken() + "(" + playerB.getScore() + ")"
        ));
    }

    @Override
    public void playerTired(PlayerModel player) {
        view.playerKo(player);
    }

    @Override
    public void scored(TeamModel team) {
        view.scored(team);
    }

    @Override
    public void outside(PlayerModel player) {
        Player playerCodeInGame = gameManager.getPlayer(player == state.teamA.player ? 0 : 1);
        gameManager.addTooltip(playerCodeInGame, "off-site!");
    }

    @Override
    public void collided() {
    }

    @Override
    public void winTheGame() {
        endGame();
    }

    @Override
    public void onEnd() {
        setScore();
    }

    @Override
    public void draw() {
        gameManager.addToGameSummary("Draw!");

        endGame();
    }

    @Override
    public void move(PlayerModel player, int from, int to) {
        view.move(player, from, to);
    }

    @Override
    public void energyChanged(PlayerModel player, int delta) {
        view.energyChanged(player, delta);
    }

    @Override
    public void hit(PlayerModel player, boolean succeeded) {
        view.hit(player, succeeded);

        if (succeeded) {
            Player playerCodeInGame = gameManager.getPlayer(player == state.teamA.player ? 0 : 1);
            gameManager.addTooltip(playerCodeInGame, "touché!");
        }
    }

    @Override
    public void defended(PlayerModel player, boolean succeeded) {
        if (succeeded) {
            Player p = gameManager.getPlayer(player == state.teamA.player ? 0 : 1);
            gameManager.addTooltip(p, "Parry!");
        }
        view.defended(player, succeeded);
    }

    @Override
    public void doped(PlayerModel player, ActionType a) {
        view.doped(player, a);
    }

    public void exportAutoDoc() {

        {
            StringBuilder b = new StringBuilder();
            b.append("<ul>\n");
            for (ActionType a : ActionType.values()) {
                b.append("<li><action>").append(a.name()).append("</action>: ");
                b.append(" league=").append(a.league + 1);
                if (a.energy != 0) b.append(" energy=").append(formatDelta(a.energy));
                if (a.energyTransfer != 0) b.append(" energyTransfer=").append(formatQuantity(a.energyTransfer));
                if (a.move != 0) b.append(" move=").append(formatDelta(a.move));
                if (a.distance != 0) b.append(" distance=").append(formatDelta(a.distance));
                if (a.drug != 0) b.append(" drug=").append(formatDelta(a.drug));
                b.append("</li>").append("\n");
            }
            b.append("</ul>\n");
            System.out.print(b.toString());
        }
        {
            StringBuilder b = new StringBuilder();
            b.append("<table>\n");
            b.append("<tr>\n");
            b.append("<th>code</th>").append("<th>energy</th>").append("<th>energyTransfer</th>")
                    .append("<th>energyTransfer</th>").append("<th>move</th>")
                    .append("<th>distance</th>").append("<th>drug</th>").append("<th>league</th>");
            for (ActionType a : ActionType.values()) {
                b.append("<tr>").append("\n");
                b.append("<td><action>").append(a.name()).append("</action><td>");
                b.append("<td>").append(formatDelta(a.energy)).append("</td>");
                b.append("<td>").append(formatQuantity(a.energyTransfer));
                b.append("<td>").append(formatDelta(a.move));
                b.append("<td>").append(formatDelta(a.distance));
                b.append("<td>").append(formatDelta(a.drug));
                b.append("<td>").append(a.league + 1);
                b.append("</tr>").append("\n");
            }
            b.append("</table>\n");
            System.out.print(b.toString());
        }
    }

}