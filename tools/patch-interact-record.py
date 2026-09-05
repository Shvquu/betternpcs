#!/usr/bin/env python3
"""Rewrites InteractionListener for the Minecraft versions where the interact packet is a record.

Minecraft 26.1 replaced ServerboundInteractPacket's visitor-style ``Handler`` with a plain record
carrying ``entityId``, ``hand``, ``location`` and ``usingSecondaryAction``. The three cases are the
same as before — attack, interact, interact-at — but they are read directly instead of dispatched.

Used by tools/sync-adapters.sh. Fails loudly rather than silently leaving the reference version in
place, because a listener that does not compile is far easier to notice than one that compiles and
reports nothing.
"""

import sys
from pathlib import Path

DISPATCH_START = "        private void decode(ServerboundInteractPacket packet) {"
NEXT_METHOD = "        private void report("

RECORD_DECODE = """        private void decode(ServerboundInteractPacket packet) {
            // Minecraft 26.1 replaced the visitor-style handler with a plain record. The same three
            // cases are still there, just read directly: an attack carries no hand, and the click
            // position is present only for the interact-at form.
            InteractionHand hand = packet.hand();
            Vec3 location = packet.location();

            report(
                    packet.entityId(),
                    hand != null,
                    // Read from the packet rather than from the player's own state: the two can
                    // disagree, because the client decides it is sneaking a moment before the
                    // server is told.
                    packet.usingSecondaryAction(),
                    hand,
                    location == null ? null : new Vector(location.x, location.y, location.z));
        }

"""


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch-interact-record.py <InteractionListener.java>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")

    try:
        start = text.index(DISPATCH_START)
        end = text.index(NEXT_METHOD, start)
    except ValueError:
        print(
            f"{path}: the reference decode() method was not found. The reference implementation "
            f"has been restructured and this patch needs updating.",
            file=sys.stderr,
        )
        return 1

    path.write_text(text[:start] + RECORD_DECODE + text[end:], encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
