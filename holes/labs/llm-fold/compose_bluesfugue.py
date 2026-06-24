#!/usr/bin/env python3
"""compose_bluesfugue.py — the SCALING test (E-llm-fold): fold ALL 14 patterns at once.

Joe's scaling idea: hand the LLM turn the UNION of both cascades (7 fugue + 7 blues = 14)
and see if it integrates them into one coherent construction — or hits a complexity knee.
The natural synthesis is a BLUES FUGUE: a bluesy subject treated fugally over a 12-bar form.

The scientific value is twofold: (1) does 14 still cohere (Joe's ear)? (2) the CROSS-PATTERN
TENSIONS the fold must reconcile (cadence-vs-turnaround, invertibility-vs-space, fugal-answer-
vs-AAB) — surfaced in E-llm-fold.md. 16 bars in A: a 12-bar fugal-exposition chorus + a
4-bar coda (episode + authentic cadence).

Run:  python3 compose_bluesfugue.py  ->  bluesfugue-A.mid
"""
import mido
from mido import Message, MidiFile, MidiTrack, MetaMessage
TPB=480
def bt(b): return int(round(b*TPB))
def tr(mel, s): return [((n+s) if n is not None else None, b) for n,b in mel]

# --- the bluesy SUBJECT (fugue-subject ∩ blues-scale-melody): A-blues, swung, 2 bars ---
SUBJ = [(64,1),(67,.667),(64,.333),(62,1),(60,1), (57,2),(60,1),(57,1)]   # E G-E D C | A.. C A
ANSWER   = tr(SUBJ, 5)    # up a 4th -> sits toward IV (serves fugal answer AND blues AAB)
BASSSUBJ = tr(SUBJ, -12)  # octave down (3rd voice entry)

# --- HARMONY: 12-bar quick-change + 4-bar coda ; all dominant-7ths (rootless comp) ---
A7=[61,64,67]; D7=[54,57,60]; E7=[56,59,62]
BARCH=[A7,D7,A7,A7, D7,D7,A7,A7, E7,D7,A7,E7,  D7,E7,E7,A7]   # [..9-12 turnaround..][..coda..]
COMP=[(c,4) for c in BARCH]

# --- BASS: boogie root-5-6-b7, except bars5-6 = the SUBJECT entry (3rd voice) ---
boog={tuple(A7):[45,52,54,55], tuple(D7):[50,57,59,60], tuple(E7):[52,59,61,62]}
def bbar(c): return [(n,1) for n in boog[tuple(c)]]
BASS = bbar(A7)+bbar(D7)+bbar(A7)+bbar(A7)         # 1-4
BASS += BASSSUBJ + bbar(A7)+bbar(A7)               # 5-6 SUBJECT entry, 7-8 boogie
BASS += bbar(E7)+bbar(D7)+bbar(A7)+bbar(E7)        # 9-12 turnaround
BASS += bbar(D7)+bbar(E7)+bbar(E7)+bbar(A7)        # 13-16 coda

# --- SOPRANO: subject; countersubject; sparse upper cpt; B-phrase; episode; cadence ---
S  = SUBJ                                                            # 1-2 SUBJECT
S += [(None,2),(64,1),(62,1),(60,2),(None,2)]                        # 3-4 countersubject (sparse)
S += [(None,4), (None,2),(64,1),(62,1)]                             # 5-6 space over bass entry
S += [(60,1),(62,1),(64,1),(67,1), (64,2),(60,2)]                   # 7-8 upper line
S += [(69,1),(67,.667),(64,.333),(62,1),(60,1), (57,2),(None,2)]    # 9-10 B-phrase (call/response)
S += [(None,2),(60,1),(59,1), (57,1),(59,1),(64,2)]                 # 11-12 turnaround upper
S += [(62,1),(65,.667),(62,.333),(60,1),(62,1), (60,1),(64,.667),(60,.333),(59,1),(60,1)]  # 13-14 EPISODE (seq)
S += [(59,1),(56,1),(57,2), (57,4)]                                 # 15-16 cadence: B G# A | A

# --- ALTO: answer; sparse cpt; episode 2nd voice; cadence ---
A  = [(None,4),(None,4)]                                             # 1-2 rest
A += ANSWER                                                          # 3-4 ANSWER (dominant/IV)
A += [(57,2),(None,2),(60,2),(None,2)]                              # 5-6 sparse cpt over bass entry
A += [(None,4), (57,2),(None,2)]                                    # 7-8
A += [(64,2),(None,2), (57,2),(None,2)]                             # 9-10
A += [(None,4), (56,2),(59,2)]                                      # 11-12
A += [(57,1),(60,.667),(57,.333),(55,1),(57,1), (55,1),(59,.667),(55,.333),(54,1),(55,1)]  # 13-14 episode 2nd voice
A += [(56,2),(None,2), (61,4)]                                      # 15-16 cadence: G# | C#
# pad ALTO to 64 beats (the explicit rests above already sum to 64)

def mono(voice,ch,vel):
    ev=[];t=0
    for n,b in voice:
        d=bt(b)
        if n is not None:
            ev+=[(t,Message('note_on',note=n,velocity=vel,channel=ch)),(t+d,Message('note_off',note=n,velocity=0,channel=ch))]
        t+=d
    return ev,t
def chords(voice,ch,vel):
    ev=[];t=0
    for ns,b in voice:
        d=bt(b)
        for n in ns: ev+=[(t,Message('note_on',note=n,velocity=vel,channel=ch)),(t+d,Message('note_off',note=n,velocity=0,channel=ch))]
        t+=d
    return ev
def drums():
    ev=[]
    for bar in range(16):
        o=bar*4
        for p in (0,.667,1,1.667,2,2.667,3,3.667): ev+=[(bt(o+p),Message('note_on',note=51,velocity=44,channel=9)),(bt(o+p)+20,Message('note_off',note=51,velocity=0,channel=9))]
        for p in (0,2): ev+=[(bt(o+p),Message('note_on',note=36,velocity=84,channel=9)),(bt(o+p)+60,Message('note_off',note=36,velocity=0,channel=9))]
        for p in (1,3): ev+=[(bt(o+p),Message('note_on',note=38,velocity=78,channel=9)),(bt(o+p)+60,Message('note_off',note=38,velocity=0,channel=9))]
    return ev
def mktrack(ev,program=None,ch=0):
    t=MidiTrack()
    if program is not None: t.append(Message('program_change',program=program,channel=ch,time=0))
    last=0
    for tm,m in sorted(ev,key=lambda e:e[0]): t.append(m.copy(time=tm-last)); last=tm
    return t

def main():
    se,sl=mono(S,0,92); ae,al=mono(A,1,80); be,bl=mono(BASS,2,88)
    assert sl==al==bl==64*TPB, (sl/TPB,al/TPB,bl/TPB)   # all voices 16 bars
    mid=MidiFile(ticks_per_beat=TPB)
    tt=MidiTrack(); tt.append(MetaMessage('set_tempo',tempo=mido.bpm2tempo(92),time=0))
    tt.append(MetaMessage('time_signature',numerator=4,denominator=4,time=0)); mid.tracks.append(tt)
    mid.tracks.append(mktrack(se,program=66,ch=0))     # tenor sax (subject voice)
    mid.tracks.append(mktrack(ae,program=68,ch=1))     # oboe-ish (answer voice) 68=oboe
    mid.tracks.append(mktrack(be,program=33,ch=2))     # bass
    mid.tracks.append(mktrack(chords(COMP,3,52),program=4,ch=3))  # quiet e-piano comp (dom7 anchor)
    mid.tracks.append(mktrack(drums(),ch=9))           # shuffle kit
    mid.save("bluesfugue-A.mid")
    print("wrote bluesfugue-A.mid ( blues-fugue, 16 bars in A, all-14-pattern fold )")

if __name__=="__main__":
    main()
