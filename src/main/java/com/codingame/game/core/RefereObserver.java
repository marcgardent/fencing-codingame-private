package com.codingame.game.core;

public interface RefereObserver {

    void playerIsKo(PlayerState player);

    void scoreAB();

    void score(TeamState team);

    void outside(PlayerState player);

    void collide();

    void winTheGame(TeamState winner, TeamState looser);

    void draw();

    void move(PlayerState player, int from, int to);

    void energyChanged(PlayerState player, int delta);

    void actionResolved(PlayerState player, byte aResolved);
}
