@echo off
set JLINK_VM_OPTIONS=
set DIR=%~dp0
"%DIR%\java" %JLINK_VM_OPTIONS% -m networkoperatorsimulator.appmodule/com.networkopsim.game.NetworkGame %*
