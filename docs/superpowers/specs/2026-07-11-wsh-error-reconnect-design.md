# Wsh Error Reconnect Design

## Goal

When an SSH connection remains available but the remote wsh helper fails to install or start, the connection-status overlay must offer a recovery action instead of suggesting that the user permanently disable wsh.

## Interaction

- Replace the `always disable wsh` button with a `Reconnect` button.
- While recovery is running, show `Reconnecting...` and prevent duplicate clicks.
- Keep the existing dismiss button. Dismissing hides the error and does not change connection configuration.
- Remove the overlay's misleading `Disconnected from ...` heading for this state; the message should identify a remote helper error while the SSH connection remains connected.

## Recovery Flow

The reconnect action will:

1. Reinstall the remote wsh helper over the existing SSH connection.
2. Disconnect the SSH connection after a successful reinstall.
3. Reconnect the SSH connection so the newly installed helper starts cleanly.

If reinstalling wsh fails, the flow stops before disconnecting. The existing SSH session remains available and the error overlay stays visible so the user can correct the remote condition, such as insufficient disk space, and try again.

If disconnecting reports that the connection is already down, recovery continues to the connect step. Other recovery failures are logged and reflected by the existing connection-status updates.

## Scope

This change only affects the wsh-error state in `ConnStatusOverlay`. Normal disconnected and stalled connection recovery behavior remains unchanged. The permanent `conn:wshenabled=false` action and its frontend handler are removed from this overlay.

## Testing

Add focused tests that verify:

- The wsh-error action is labeled `Reconnect`, not `always disable wsh`.
- Recovery calls reinstall, disconnect, and connect in order.
- A reinstall failure does not disconnect the SSH connection.
- The reconnect action cannot be started twice concurrently.
