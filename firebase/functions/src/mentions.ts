// « @Prénom » dans un commentaire (Kévin, 19/11): the comment keeps the members it names
// (mentionIds). Each of them is told once, directly, « Camille t'a nommé dans un commentaire »,
// instead of the usual « a commenté ». Never the one who wrote it, nor a child without the app.
// The note of a coup de main names members the same way.

/** The members named by a comment, among [withApp], without its author, each once, sorted. */
export function namedIn(comment: { authorId: string; mentionIds?: unknown }, withApp: Set<string>): string[] {
  const ids = Array.isArray(comment.mentionIds) ? comment.mentionIds : [];
  const named = ids.filter((id): id is string => typeof id === 'string' && id !== comment.authorId && withApp.has(id));
  return [...new Set(named)].sort();
}

/** « Camille t'a nommé dans un commentaire ». */
export function mentionTitle(authorName: string): string {
  return `${authorName} t'a nommé dans un commentaire`;
}

/**
 * « @Prénom » in the note of an event or a coup de main (Kévin, 19/11): the members named by this
 * write and not before, among [withApp], never the one who wrote it. Each is told once, on the
 * write that names them; changing the rest of the note tells nobody again.
 */
export function newlyNamed(before: { mentionIds?: unknown } | undefined, after: { mentionIds?: unknown }, actorId: string | null, withApp: Set<string>): string[] {
  const ids = (d: { mentionIds?: unknown } | undefined) =>
    (Array.isArray(d?.mentionIds) ? d.mentionIds : []).filter((id): id is string => typeof id === 'string');
  const already = new Set(ids(before));
  return [...new Set(ids(after))].filter((id) => !already.has(id) && id !== actorId && withApp.has(id)).sort();
}

/** « Camille t'a nommé dans « Pique-nique » », and the start of the note. */
export function noteMentionTitle(actorName: string, title: string): string {
  return `${actorName} t'a nommé dans « ${title} »`;
}

const NOTE_PREVIEW = 120;

/** The note on one line, cut at [NOTE_PREVIEW] characters. */
export function notePreview(note: string | null | undefined): string {
  const line = (note ?? '').replace(/\s+/g, ' ').trim();
  return line.length <= NOTE_PREVIEW ? line : `${line.slice(0, NOTE_PREVIEW - 1).trimEnd()}…`;
}
