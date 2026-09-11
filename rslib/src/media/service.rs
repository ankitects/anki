use std::collections::HashSet;
use std::path::Path;

// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;
use anki_proto::media::AddMediaFileRequest;
use anki_proto::media::AddMediaFromPathRequest;
use anki_proto::media::CheckMediaResponse;
use anki_proto::media::TrashMediaFilesRequest;

use crate::collection::Collection;
use crate::error;
use crate::error::OrNotFound;
use crate::notes::service::to_i64s;
use crate::notetype::NotetypeId;
use crate::text::extract_media_refs;

impl crate::services::MediaService for Collection {
    fn check_media(&mut self) -> error::Result<CheckMediaResponse> {
        self.transact_no_undo(|col| {
            let mut checker = col.media_checker()?;
            let mut output = checker.check()?;

            let mut report = checker.summarize_output(&mut output);
            col.report_media_field_referencing_templates(&mut report)?;

            Ok(CheckMediaResponse {
                unused: output.unused,
                missing: output.missing,
                missing_media_notes: to_i64s(output.missing_media_notes),
                report,
                have_trash: output.trash_count > 0,
            })
        })
    }

    fn add_media_file(&mut self, input: AddMediaFileRequest) -> error::Result<generic::String> {
        Ok(self
            .media()?
            .add_file(&input.desired_name, &input.data)?
            .to_string()
            .into())
    }

    fn add_media_from_path(
        &mut self,
        input: AddMediaFromPathRequest,
    ) -> error::Result<generic::String> {
        let base_name = Path::new(&input.path)
            .file_name()
            .unwrap_or_default()
            .to_str()
            .unwrap_or_default();
        let data = std::fs::read(&input.path)?;
        Ok(self.media()?.add_file(base_name, &data)?.to_string().into())
    }

    fn trash_media_files(&mut self, input: TrashMediaFilesRequest) -> error::Result<()> {
        self.media()?.remove_files(&input.fnames)
    }

    fn empty_trash(&mut self) -> error::Result<()> {
        self.media_checker()?.empty_trash()
    }

    fn restore_trash(&mut self) -> error::Result<()> {
        self.media_checker()?.restore_trash()
    }

    fn extract_static_media_files(
        &mut self,
        ntid: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<generic::StringList> {
        let ntid = NotetypeId::from(ntid);
        let notetype = self.storage.get_notetype(ntid)?.or_not_found(ntid)?;
        let mut files: HashSet<String> = HashSet::new();
        let mut inserter = |name: String| {
            files.insert(name);
        };
        notetype.gather_media_names(&mut inserter);

        Ok(files.into_iter().collect::<Vec<_>>().into())
    }

    fn extract_media_files(
        &mut self,
        html: anki_proto::generic::String,
    ) -> error::Result<generic::StringList> {
        let files = extract_media_refs(&html.val)
            .iter()
            .map(|r| r.fname_decoded.to_string())
            .collect::<Vec<_>>();
        Ok(files.into())
    }

    fn get_absolute_media_path(
        &mut self,
        path: anki_proto::generic::String,
    ) -> error::Result<generic::String> {
        Ok(self
            .media()?
            .media_folder
            .join(path.val)
            .to_string_lossy()
            .to_string()
            .into())
    }
}

#[cfg(test)]
mod tests {
    use anki_io::create_dir;
    use anki_io::read_file;
    use anki_io::write_file;
    use tempfile::tempdir;
    use tempfile::TempDir;

    use super::*;
    use crate::collection::CollectionBuilder;
    use crate::media::files::trash_folder;
    use crate::prelude::*;
    use crate::services::MediaService;

    pub(crate) fn common_setup() -> Result<(TempDir, Collection)> {
        let dir = tempdir()?;
        let media_folder = dir.path().join("media");
        create_dir(&media_folder)?;
        let media_db = dir.path().join("media.db");
        let col_path = dir.path().join("col.anki2");

        let mut col = CollectionBuilder::new(col_path)
            .set_media_paths(media_folder, media_db)
            .build()?;

        assert_eq!(
            col.add_media_file(AddMediaFileRequest {
                desired_name: "unused.txt".into(),
                data: b"blah".into(),
            })?
            .val,
            "unused.txt"
        );
        assert_eq!(
            col.add_media_file(AddMediaFileRequest {
                desired_name: "ABC.jpg".into(),
                data: b"foo".into(),
            })?
            .val,
            "abc.jpg"
        );

        Ok((dir, col))
    }

    #[test]
    fn media_check() -> Result<()> {
        let (dir, mut col) = common_setup()?;

        let path = dir.path().join("abc.JPG");
        write_file(&path, b"blah")?;
        let path = path.to_str().unwrap().to_owned();
        assert_eq!(
            col.add_media_from_path(AddMediaFromPathRequest { path })?
                .val,
            "abc-5bf1fd927dfb8679496a2e6cf00cbe50c1c87145.jpg"
        );

        let basic = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = basic.new_note();
        note.set_field(0, "lol").unwrap();
        note.set_field(1, r#"<img src="abc.jpg">"#).unwrap();
        col.add_note(&mut note, DeckId(1))?;
        let mut note = basic.new_note();
        note.set_field(0, "guh")?;
        note.set_field(1, r#"[sound:guh.mp3]"#)?;
        col.add_note(&mut note, DeckId(1))?;

        let CheckMediaResponse {
            mut unused,
            missing,
            missing_media_notes,
            report: _,
            have_trash,
        } = col.check_media()?;

        unused.sort();
        assert_eq!(
            unused,
            &[
                "abc-5bf1fd927dfb8679496a2e6cf00cbe50c1c87145.jpg",
                "unused.txt"
            ]
        );
        assert_eq!(missing, &["guh.mp3"]);
        assert_eq!(missing_media_notes, &[note.id.0]);
        assert!(!have_trash);

        Ok(())
    }

    fn trash_common(col: &mut Collection) -> Result<()> {
        let CheckMediaResponse {
            mut unused,
            have_trash,
            ..
        } = col.check_media()?;

        unused.sort();
        assert_eq!(unused, &["abc.jpg", "unused.txt"]);
        assert!(!have_trash);

        col.trash_media_files(TrashMediaFilesRequest {
            fnames: vec!["unused.txt".into()],
        })?;

        let CheckMediaResponse {
            unused, have_trash, ..
        } = col.check_media()?;

        assert_eq!(unused, &["abc.jpg"]);
        assert!(have_trash);
        assert!(trash_folder(&col.media_folder)?
            .read_dir()?
            .any(|dentry| dentry.unwrap().file_name() == "unused.txt"));

        Ok(())
    }

    #[test]
    fn restore_trash_check() -> Result<()> {
        let (_dir, mut col) = common_setup()?;
        trash_common(&mut col)?;

        col.restore_trash()?;

        let CheckMediaResponse {
            mut unused,
            have_trash,
            ..
        } = col.check_media()?;

        unused.sort();
        assert_eq!(unused, &["abc.jpg", "unused.txt"]);
        assert!(!have_trash);
        assert_eq!(trash_folder(&col.media_folder)?.read_dir()?.count(), 0);

        Ok(())
    }

    #[test]
    fn empty_trash_check() -> Result<()> {
        let (_dir, mut col) = common_setup()?;
        trash_common(&mut col)?;

        col.empty_trash()?;

        let CheckMediaResponse {
            mut unused,
            have_trash,
            ..
        } = col.check_media()?;

        unused.sort();
        assert_eq!(unused, &["abc.jpg"]);
        assert!(!have_trash);
        assert_eq!(trash_folder(&col.media_folder)?.read_dir()?.count(), 0);

        Ok(())
    }

    #[test]
    fn extract_check() -> Result<()> {
        let (_dir, mut col) = common_setup()?;

        let mut nt = crate::notetype::stock::basic(&col.tr);
        nt.name = "new".into();
        nt.config.css += r#"
@font-face {
	font-family: 'Noto Sans JP';
	src: local('Noto Sans JP Regular'), local('NotoSansJP-Regular'), url("_NotoSansJP-Regular.woff2")  format('woff2');
}*
        "#;
        nt.add_template(
            "1",
            r#"<script src="_foo.js"></script>{{Front}}[sound:_lol.ogg]"#,
            r#"<img src="def.png">{{Back}}<img src="_ghi.gif">"#,
        );
        col.add_notetype(&mut nt, false)?;

        let mut note = nt.new_note();
        note.set_field(0, "lol").unwrap();
        note.set_field(1, r#"<img src="jxlwhen.jpg"> 123 [sound:lossless.flac]"#)
            .unwrap();
        col.add_note(&mut note, DeckId(1))?;

        let mut res = col
            .extract_static_media_files(anki_proto::notetypes::NotetypeId { ntid: nt.id.0 })?
            .vals;

        res.sort();
        assert_eq!(
            res,
            &[
                "_NotoSansJP-Regular.woff2",
                "_foo.js",
                "_ghi.gif",
                "_lol.ogg",
            ]
        );

        let mut res = col
            .extract_media_files(anki_proto::generic::String {
                val: note.fields()[1].to_owned(),
            })?
            .vals;

        res.sort();
        assert_eq!(res, &["jxlwhen.jpg", "lossless.flac"]);

        Ok(())
    }

    #[test]
    fn get_absolute_media_path_should_point_to_file() -> Result<()> {
        let (_dir, mut col) = common_setup()?;

        let path = col
            .get_absolute_media_path(anki_proto::generic::String {
                val: "abc.jpg".into(),
            })?
            .val;

        assert_eq!(read_file(path)?, b"foo");

        Ok(())
    }
}
