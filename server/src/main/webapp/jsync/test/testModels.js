/*   Copyright [2009] Digiting Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

$sync = $sync || {};
$sync.test = $sync.test || {};

$sync.test.named = $sync.manager.defineKind("$sync.test.nameObj", ['name']);
$sync.test.referenced = $sync.manager.defineKind("$sync.test.refObj", ['ref']);
$sync.test.valued = $sync.manager.defineKind("$sync.test.valueObj", ['value']);
$sync.test.paragraph = $sync.manager.defineKind("$sync.test.paragraph", ['text']);

