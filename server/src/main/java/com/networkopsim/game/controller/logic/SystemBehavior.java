package com.networkopsim.game.controller.logic;

import com.networkopsim.game.model.core.Packet;
import com.networkopsim.game.model.core.System;
import com.networkopsim.game.model.enums.NetworkEnums;

public interface SystemBehavior {
  void receivePacket(System system, Packet packet, GameEngine gameEngine, boolean isPredictionRun, boolean enteredCompatibly);
  void processQueue(System system, GameEngine gameEngine, boolean isPredictionRun);
  void attemptPacketGeneration(System system, GameEngine gameEngine, long currentSimTimeMs, boolean isPredictionRun);
  NetworkEnums.SystemType getSystemType();
}