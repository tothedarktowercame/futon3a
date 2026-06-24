#!/usr/bin/env python3
"""compose_blues.py — the OUTPUT of an LLM-turn fold over blues-cascade.flexiarg.

2nd data point for E-llm-fold (same mechanism, different genre). The 7 NL blues patterns
were read by an LLM turn (claude-2, 2026-06-24) and grounded against the circumstance
"compose one 12-bar blues chorus in A" → this construction. The patterns fix FORM,
HARMONY and FEEL; the actual licks/bass/turnaround notes are the FREE content (policy-holes).

Run:  python3 compose_blues.py   ->  blues-A.mid
"""
import mido
from mido import Message, MidiFile, MidiTrack, MetaMessage

TPB = 480
def bt(b): return int(round(b * TPB))

# --- HARMONY: 12-bar quick-change in A ; all dominant 7ths ; rootless comp voicings ---
# [twelve-bar-form] I IV I I / IV IV I I / V IV I V   [quick-change: bar2=IV]  [dominant-sevenths]
A7 = [61,64,67]   # C#4 E4 G4   (rootless A7)
D7 = [54,57,60]   # F#3 A3 C4   (rootless D7)
E7 = [56,59,62]   # G#3 B3 D4   (rootless E7)
BARS = [A7,D7,A7,A7, D7,D7,A7,A7, E7,D7,A7,E7]
CHORDS = [(c,4) for c in BARS]

# --- BASS: boogie root-5-6-b7 per bar [dominant-sevenths + shuffle drive] ---
boog = {tuple(A7):[45,52,54,55], tuple(D7):[50,57,59,60], tuple(E7):[52,59,61,62]}
BASS = []
for c in BARS:
    BASS += [(n,1) for n in boog[tuple(c)]]

# --- MELODY: A blues scale (A C D Eb E G), AAB call-and-response, swung 8ths, space ---
# [blues-scale-melody] [call-and-response] [shuffle-feel]
lineA = [
    (57,1),(60,.667),(62,.333),(63,.667),(64,.333),(67,1),   # bar: A  C-D  Eb-E  G  (swung ascent, blue notes)
    (67,1),(64,1),(62,2),                                     # bar: G  E  D(hold)   (the call lands)
    (None,2),(60,1),(57,1),                                   # bar: (space)  C  A    (response, air)
    (None,4),                                                 # bar: (space)
]
lineB = [
    (64,1),(67,.667),(69,.333),(67,1),(64,1),                 # bar9  (over V): E  G-A  G  E
    (62,1),(60,1),(57,2),                                     # bar10 (over IV): D  C  A(hold)
    (57,2),(None,1),(60,1),                                   # bar11 (over I):  A(hold) . C
    (62,1),(61,1),(60,1),(59,1),                              # bar12 TURNAROUND: D C# C B -> pull to V
]
MELODY = lineA + lineA + lineB   # AAB

# --- DRUMS (ch9): shuffle ride + backbeat snare + kick ; [shuffle-feel] ---
def drum_events():
    ev = []
    for bar in range(12):
        o = bar * 4
        for p in (0,.667,1,1.667,2,2.667,3,3.667):           # swung ride
            ev += [(bt(o+p), Message('note_on',note=51,velocity=52,channel=9)),
                   (bt(o+p)+20, Message('note_off',note=51,velocity=0,channel=9))]
        for p in (0,2):                                       # kick
            ev += [(bt(o+p), Message('note_on',note=36,velocity=92,channel=9)),
                   (bt(o+p)+60, Message('note_off',note=36,velocity=0,channel=9))]
        for p in (1,3):                                       # snare backbeat
            ev += [(bt(o+p), Message('note_on',note=38,velocity=84,channel=9)),
                   (bt(o+p)+60, Message('note_off',note=38,velocity=0,channel=9))]
    return ev

def mono_events(voice, ch, vel):
    ev=[]; t=0
    for note,b in voice:
        d=bt(b)
        if note is not None:
            ev += [(t, Message('note_on',note=note,velocity=vel,channel=ch)),
                   (t+d, Message('note_off',note=note,velocity=0,channel=ch))]
        t += d
    return ev

def chord_events(voice, ch, vel):
    ev=[]; t=0
    for notes,b in voice:
        d=bt(b)
        for n in notes:
            ev += [(t, Message('note_on',note=n,velocity=vel,channel=ch)),
                   (t+d, Message('note_off',note=n,velocity=0,channel=ch))]
        t += d
    return ev

def track(events, program=None, ch=0):
    tr=MidiTrack()
    if program is not None:
        tr.append(Message('program_change', program=program, channel=ch, time=0))
    last=0
    for t,msg in sorted(events, key=lambda e:e[0]):
        tr.append(msg.copy(time=t-last)); last=t
    return tr

def main():
    mid=MidiFile(ticks_per_beat=TPB)
    tempo=MidiTrack()
    tempo.append(MetaMessage('set_tempo', tempo=mido.bpm2tempo(96), time=0))
    tempo.append(MetaMessage('time_signature', numerator=4, denominator=4, time=0))
    mid.tracks.append(tempo)
    mid.tracks.append(track(mono_events(MELODY,0,92), program=66, ch=0))   # 66 tenor sax (the head)
    mid.tracks.append(track(chord_events(CHORDS,1,64), program=4,  ch=1))  # 4 electric piano (comp)
    mid.tracks.append(track(mono_events(BASS,2,90),  program=33, ch=2))    # 33 electric bass (boogie)
    mid.tracks.append(track(drum_events(), ch=9))                          # ch10 drums (shuffle)
    mid.save("blues-A.mid")
    print("wrote blues-A.mid ( melody+comp+bass+drums, 12-bar quick-change in A )")

if __name__ == "__main__":
    main()
