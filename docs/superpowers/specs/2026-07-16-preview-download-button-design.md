# Preview Download Button Design

## Goal

Expose the existing remote-file download action directly in the preview block header, immediately to the right of the Settings button.

## Behavior

- Show a Download icon only when the preview has loaded a remote regular file.
- Do not show it for directories, local files, missing files, loading states, or disconnected previews.
- Place it after Settings and before the existing magnify/add-to-layout and close controls.
- Use the same Electron download operation and remote URI formatting as the existing **Download File** settings-menu action.
- Keep the existing settings-menu action unchanged.

## Architecture

Add an optional post-settings icon-button atom to the shared `ViewModel` contract and render it directly after Settings in the block header. `PreviewModel` supplies a Download declaration derived from its connection and loaded file information. The click handler delegates to a small preview-model download method so visibility and behavior remain independently testable without coupling the generic header to preview-specific file logic.

## Error Handling

The Electron download API remains responsible for showing the save flow and reporting failures, matching the existing settings-menu behavior. If file information or a remote connection is unavailable, the button is absent and no download can be triggered.

## Testing

- Verify the shared header renders post-settings buttons after Settings.
- Verify preview download-button eligibility for remote files and ineligibility for local files, directories, and unavailable file information.
- Verify clicking the declaration formats the remote URI and invokes the existing Electron download API.
- Run the focused Vitest files and the relevant TypeScript check available in the repository.
