#!/usr/bin/env python3
"""compose_fugue.py — the OUTPUT of an LLM-turn fold over a music-theory cascade.

This is NOT a deterministic compiler. The NL patterns in music-cascade.multiarg were
read by an LLM turn (claude-2, 2026-06-24) which GROUNDED each pattern's IF/HOWEVER
against the circumstance ("compose a 3-voice fugue exposition in C major") and emitted
this construction. The score below is that fold's product; this script just renders it.

The WIRING (which pattern landed where) is in the section comments + WIRING below.
The POLICY-HOLES (what the patterns left FREE — the actual notes) are the note values
themselves: the patterns fix the STRUCTURE (subject in tonic, answer in dominant,
cadence V->I), never the CONTENT (the tune C-D-E-G-F-E-D was a free choice).

Run:  python3 compose_fugue.py   ->  fugue-c-major.mid
"""
import mido
from mido import Message, MidiFile, MidiTrack, MetaMessage

TPB = 480  # ticks per quarter

# (midi-note | None=rest, beats).  C4 = 60.  Each voice sums to 32 beats (8 bars of 4/4).
# Subject motif (FREE content / policy-hole): C D E G F E D
SOPRANO = [
    # bars 1-2  SUBJECT in C (tonic)                 [pattern: fugue-subject]
    (60,1),(62,1),(64,1),(67,1),  (65,1),(64,1),(62,2),
    # bars 3-4  COUNTERSUBJECT over the answer       [pattern: countersubject]
    (71,1),(72,1),(74,1),(77,1),  (76,1),(74,1),(72,2),
    # bars 5-6  upper line over the bass subject     [pattern: voice-leading]
    (67,1),(69,1),(71,1),(74,1),  (72,1),(71,1),(69,2),
    # bars 7-8  authentic cadence (LT B->C)          [pattern: authentic-cadence]
    (74,1),(74,1),(71,1),(71,1),  (72,4),
]
ALTO = [
    (None,4),(None,4),                                # bars 1-2 rest
    # bars 3-4  ANSWER in the dominant (G)           [pattern: subject->answer]
    (67,1),(69,1),(71,1),(74,1),  (72,1),(71,1),(69,2),
    # bars 5-6  inner line, triadic                  [pattern: voice-leading]
    (64,1),(65,1),(67,1),(71,1),  (69,1),(67,1),(65,2),
    # bars 7-8  the 7th of V (F) resolving down to E [pattern: authentic-cadence]
    (65,1),(65,1),(65,1),(65,1),  (64,4),
]
BASS = [
    (None,4),(None,4),(None,4),(None,4),              # bars 1-4 rest
    # bars 5-6  SUBJECT in C, bass (3rd entry)       [pattern: exposition-entries]
    (48,1),(50,1),(52,1),(55,1),  (53,1),(52,1),(50,2),
    # bars 7-8  V (G) -> I (C)                        [pattern: authentic-cadence]
    (43,1),(43,1),(43,1),(43,1),  (48,4),
]

def track_for(voice, program=19):  # 19 = church organ (sustained -> counterpoint audible)
    tr = MidiTrack()
    tr.append(Message('program_change', program=program, time=0))
    pending = 0  # accumulated rest ticks -> delta for the next note_on
    for note, beats in voice:
        dur = int(beats * TPB)
        if note is None:
            pending += dur
        else:
            tr.append(Message('note_on',  note=note, velocity=78, time=pending))
            tr.append(Message('note_off', note=note, velocity=0,  time=dur))
            pending = 0
    return tr

def main():
    mid = MidiFile(ticks_per_beat=TPB)
    tempo = MidiTrack()
    tempo.append(MetaMessage('set_tempo', tempo=mido.bpm2tempo(92), time=0))
    tempo.append(MetaMessage('time_signature', numerator=4, denominator=4, time=0))
    mid.tracks.append(tempo)
    for v in (SOPRANO, ALTO, BASS):
        mid.tracks.append(track_for(v))
    out = "fugue-c-major.mid"
    mid.save(out)
    print("wrote", out, "(", len(mid.tracks)-1, "voices, 8 bars, C major )")

if __name__ == "__main__":
    main()
